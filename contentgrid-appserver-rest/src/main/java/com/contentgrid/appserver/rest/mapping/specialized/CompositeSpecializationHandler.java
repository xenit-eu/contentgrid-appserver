package com.contentgrid.appserver.rest.mapping.specialized;

import com.contentgrid.appserver.application.model.Application;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

@RequiredArgsConstructor
public class CompositeSpecializationHandler<T> implements SpecializationHandler<T> {

    @NonNull
    private final List<SpecializationHandler<T>> delegates;

    @Override
    public Optional<Specializer> getSpecializerFor(T lookup) {
        return CompositeSpecializer.maybeCreate(delegates.stream().flatMap(delegate -> delegate.getSpecializerFor(
                lookup).stream()).toList());
    }

    private record CompositeSpecializer(@NonNull List<Specializer> specializers) implements Specializer {

            public static Optional<Specializer> maybeCreate(List<Specializer> list) {
                return switch (list.size()) {
                    case 0 -> Optional.empty();
                    case 1 -> Optional.of(list.getFirst());
                    default -> Optional.of(new CompositeSpecializer(list));
                };
            }

            @Override
            public void validate(RequestMappingInfo requestMappingInfo) {
                for (var specializer : specializers) {
                    specializer.validate(requestMappingInfo);
                }
            }

            @Override
            public Stream<RequestMappingInfo> specialize(Application application, RequestMappingInfo requestMappingInfo) {
                var completedStream = Stream.of(requestMappingInfo);
                for (var specializer : specializers) {
                    completedStream = completedStream.flatMap(rmi -> specializer.specialize(application, rmi));
                }
                return completedStream;
            }

        @Override
        public void handleMatch(Application application, HttpServletRequest request) {
            for (var specializer : specializers) {
                specializer.handleMatch(application, request);
            }
        }
    }
}
