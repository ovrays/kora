package ru.tinkoff.kora.test.extension.junit5.add;

import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTest;
import ru.tinkoff.kora.test.extension.junit5.KoraAppTestGraphModifier;
import ru.tinkoff.kora.test.extension.junit5.KoraGraphModification;
import ru.tinkoff.kora.test.extension.junit5.TestComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.LifecycleComponent;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestApplication;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent2;
import ru.tinkoff.kora.test.extension.junit5.testdata.TestComponent23;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@KoraAppTest(value = TestApplication.class, components = TestComponent23.class)
public class AddComponentTests implements KoraAppTestGraphModifier {

    @Tag(LifecycleComponent.class)
    @TestComponent
    private TestComponent2 original;
    @Tag(TestComponent23.class)
    @TestComponent
    private LifecycleComponent added;

    @Override
    public @Nonnull KoraGraphModification graph() {
        return KoraGraphModification.create()
                .addComponent(LifecycleComponent.class, List.of(TestComponent23.class), () -> (LifecycleComponent) () -> "?");
    }

    @Test
    void addedBean() {
        assertEquals("2", original.get());
        assertEquals("?", added.get());
    }
}
