package com.template.api.architecture;

import com.template.api.ApiApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Spring Modulith Architectural Verification")
class ModulithStructureTest {

    @Test
    @DisplayName("Debe verificar que la estructura modular y fronteras de Modulith son válidas (ARC-01)")
    void should_verify_modulith_structure_when_inspected() {
        ApplicationModules modules = ApplicationModules.of(ApiApplication.class);
        modules.verify();

        ApplicationModule sharedModule = modules.getModuleByName("shared")
                .orElseThrow(() -> new AssertionError("El módulo 'shared' debe ser detectado por Spring Modulith"));

        ApplicationModule orderingModule = modules.getModuleByName("ordering")
                .orElseThrow(() -> new AssertionError("El módulo 'ordering' debe ser detectado por Spring Modulith"));

        assertThat(sharedModule.getDisplayName()).isEqualTo("Shared");
        assertThat(orderingModule.getDisplayName()).isEqualTo("Ordering");
    }

    @Test
    @DisplayName("Debe generar la documentación y diagramas de arquitectura de Modulith (ARC-03)")
    void should_generate_architecture_documentation_when_invoked() {
        new Documenter(ApplicationModules.of(ApiApplication.class))
                .writeDocumentation()
                .writeIndividualModulesAsPlantUml();
    }
}
