package ru.tinkoff.kora.test.extension.junit5.config;

import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.ConfigValue;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTestConfigModifier;
import ru.tinkoff.kora.test.extension.junit5.KoraConfigModification;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestConfigApplication;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@KoraAppTest(TestConfigApplication.class)
public class ConfigWithFileWithPropertyTests implements KoraAppTestConfigModifier {
    @TestComponent
    Config config;

    @Override
    public @Nonnull KoraConfigModification config() {
        return KoraConfigModification.ofResourceFile("config/application-env.conf")
                .withSystemProperty("ENV_SECOND", "value");
    }

    @Test
    void parameterConfigFromMethodInjected() {
        assertThat(config.get("myconfig")).isInstanceOf(ConfigValue.ObjectValue.class);
        assertThat(config.get("myconfig.myinnerconfig")).isInstanceOf(ConfigValue.ObjectValue.class);
        assertEquals("value", config.get("myconfig.myinnerconfig.second").asString());
    }
}
