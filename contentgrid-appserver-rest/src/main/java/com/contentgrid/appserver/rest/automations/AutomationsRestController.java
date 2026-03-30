package com.contentgrid.appserver.rest.automations;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.domain.automations.AutomationsModel.AutomationModel;
import com.contentgrid.appserver.domain.automations.AutomationsModelResolver;
import com.contentgrid.appserver.rest.links.factory.LinkFactoryProvider;
import com.contentgrid.thunx.spring.security.AbacContextSupplier;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/.contentgrid/automations")
@RequiredArgsConstructor
public class AutomationsRestController {

    @NonNull
    private AutomationsModelResolver modelResolver;
    @NonNull
    private final AutomationRepresentationModelAssembler assembler;
    @NonNull
    private final AbacContextSupplier abacContextSupplier;

    private static final AutomationModelPermissionEvaluator PERMISSION_EVALUATOR = new AutomationModelPermissionEvaluator();

    @GetMapping
    public ResponseEntity<CollectionModel<AutomationRepresentationModel>> getAutomations(Application application, LinkFactoryProvider linkFactoryProvider) {
        var model = modelResolver.resolve(application);
        var automations = filterAutomations(model.getAutomations());
        return ResponseEntity.ok(assembler.withContext(application, linkFactoryProvider).toCollectionModel(automations));
    }

    @GetMapping("{id}")
    public ResponseEntity<AutomationRepresentationModel> getAutomation(
            Application application,
            LinkFactoryProvider linkFactoryProvider,
            @PathVariable(name = "id") String id
    ) {
        var model = modelResolver.resolve(application);
        var automation = filterAutomations(model.getAutomations()).stream()
                .filter(aut -> aut.getId().equals(id))
                .findFirst();

        return automation.map(aut -> assembler.toModel(aut, new AutomationRepresentationModelContext(application, linkFactoryProvider), true))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private List<AutomationModel> filterAutomations(List<AutomationModel> automations) {
        var abacContext = abacContextSupplier.getAbacContext();
        if (abacContext == null) {
            return automations;
        }
        return automations.stream()
                .filter(automation -> PERMISSION_EVALUATOR.isAllowed(abacContext, automation))
                .toList();
    }

}
