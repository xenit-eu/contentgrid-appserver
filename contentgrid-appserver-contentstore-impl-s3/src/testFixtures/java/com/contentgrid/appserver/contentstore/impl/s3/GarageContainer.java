package com.contentgrid.appserver.contentstore.impl.s3;

import com.github.dockerjava.api.command.InspectContainerResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

/**
 * A TestContainer for <a href="https://garagehq.deuxfleurs.fr/">Garage</a>,
 * an S3-compatible object store that replaces Minio in tests.
 * <p>
 * Automatically initializes a single-node Garage cluster on startup:
 * assigns a cluster layout, creates an API key, and exposes S3 connection details.
 */
public class GarageContainer extends GenericContainer<GarageContainer> {

    private static final DockerImageName DEFAULT_IMAGE = DockerImageName.parse("dxflrs/garage:v2.2.0");

    private static final int S3_PORT = 3900;
    private static final int ADMIN_PORT = 3903;
    private static final String KEY_NAME = "testkey";

    private static final String GARAGE_TOML = """
            metadata_dir = "/tmp/meta"
            data_dir = "/tmp/data"
            db_engine = "sqlite"
            
            replication_factor = 1
            
            rpc_bind_addr = "[::]:3901"
            rpc_public_addr = "127.0.0.1:3901"
            rpc_secret = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            
            [s3_api]
            s3_region = "us-east-1"
            api_bind_addr = "[::]:3900"
            root_domain = ".s3.garage.localhost"
            
            [s3_web]
            bind_addr = "[::]:3902"
            root_domain = ".web.garage.localhost"
            index = "index.html"
            
            [admin]
            api_bind_addr = "[::]:3903"
            admin_token = "admin-token"
            """;

    private String accessKey;
    private String secretKey;

    public GarageContainer() {
        this(DEFAULT_IMAGE);
    }

    public GarageContainer(String dockerImageName) {
        this(DockerImageName.parse(dockerImageName));
    }

    public GarageContainer(DockerImageName dockerImageName) {
        super(dockerImageName);
        withExposedPorts(S3_PORT, ADMIN_PORT);
        withCopyToContainer(Transferable.of(GARAGE_TOML), "/etc/garage.toml");
        // Wait for the S3 API server to be ready before running CLI initialization
        waitingFor(Wait.forLogMessage(".*S3 API server listening on.*", 1));
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        try {
            initCluster();
            initKey();
            grantOwnerPermission();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to initialize Garage container", e);
        }
    }

    private void initCluster() throws IOException, InterruptedException {
        // Get the node ID from `garage status`
        var statusResult = execInContainer("/garage", "status");
        if (statusResult.getExitCode() != 0) {
            throw new RuntimeException("garage status failed: " + statusResult.getStderr());
        }

        // Parse node ID from the status output - it's a hex string in the first column of the table
        var nodeIdPattern = Pattern.compile("^([0-9a-f]{16})\\s+", Pattern.MULTILINE);
        var matcher = nodeIdPattern.matcher(statusResult.getStdout());
        if (!matcher.find()) {
            throw new RuntimeException("Could not parse node ID from garage status output: " + statusResult.getStdout());
        }
        var nodeId = matcher.group(1);

        // Assign layout
        var assignResult = execInContainer("/garage", "layout", "assign", "-z", "dc1", "-c", "1G", nodeId);
        if (assignResult.getExitCode() != 0) {
            throw new RuntimeException("garage layout assign failed: " + assignResult.getStderr());
        }

        // Apply layout
        var applyResult = execInContainer("/garage", "layout", "apply", "--version", "1");
        if (applyResult.getExitCode() != 0) {
            throw new RuntimeException("garage layout apply failed: " + applyResult.getStderr());
        }
    }

    private void initKey() throws IOException, InterruptedException {
        var keyResult = execInContainer("/garage", "key", "create", KEY_NAME);
        if (keyResult.getExitCode() != 0) {
            throw new RuntimeException("garage key create failed: " + keyResult.getStderr());
        }

        // Parse access key (Key ID) and secret key from the output
        var output = keyResult.getStdout();

        var keyIdPattern = Pattern.compile("Key ID:\\s+(\\S+)");
        var keyIdMatcher = keyIdPattern.matcher(output);
        if (!keyIdMatcher.find()) {
            throw new RuntimeException("Could not parse Key ID from garage key create output: " + output);
        }
        this.accessKey = keyIdMatcher.group(1);

        var secretPattern = Pattern.compile("Secret key:\\s+(\\S+)");
        var secretMatcher = secretPattern.matcher(output);
        if (!secretMatcher.find()) {
            throw new RuntimeException("Could not parse Secret key from garage key create output: " + output);
        }
        this.secretKey = secretMatcher.group(1);
    }

    /**
     * Grant the test API key the {@code CreateBucket} owner permission so that
     * S3 SDK calls like {@code client.makeBucket()} work.
     */
    private void grantOwnerPermission() throws IOException, InterruptedException {
        var result = execInContainer("/garage", "key", "allow", KEY_NAME, "--create-bucket");
        if (result.getExitCode() != 0) {
            throw new RuntimeException("garage key allow --create-bucket failed: " + result.getStderr());
        }
    }

    /**
     * Create a bucket and grant the test API key read/write access.
     */
    public void createBucket(String bucketName) {
        try {
            var createResult = execInContainer("/garage", "bucket", "create", bucketName);
            if (createResult.getExitCode() != 0) {
                throw new RuntimeException("garage bucket create failed: " + createResult.getStderr());
            }

            var allowResult = execInContainer("/garage", "bucket", "allow",
                    "--read", "--write", "--owner", bucketName, "--key", KEY_NAME);
            if (allowResult.getExitCode() != 0) {
                throw new RuntimeException("garage bucket allow failed: " + allowResult.getStderr());
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to create bucket " + bucketName, e);
        }
    }

    /**
     * Returns the S3 API endpoint URL.
     */
    public String getS3URL() {
        return "http://" + getHost() + ":" + getMappedPort(S3_PORT);
    }

    /**
     * Returns the access key ID for the test API key.
     */
    public String getAccessKey() {
        return accessKey;
    }

    /**
     * Returns the secret key for the test API key.
     */
    public String getSecretKey() {
        return secretKey;
    }
}
