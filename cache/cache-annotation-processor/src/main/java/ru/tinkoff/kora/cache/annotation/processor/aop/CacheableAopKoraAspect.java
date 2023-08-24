package ru.tinkoff.kora.cache.annotation.processor.aop;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import ru.tinkoff.kora.annotation.processor.common.MethodUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.cache.annotation.processor.CacheOperation;
import ru.tinkoff.kora.cache.annotation.processor.CacheOperationUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.PrimitiveType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;

public class CacheableAopKoraAspect extends AbstractAopCacheAspect {

    private static final ClassName ANNOTATION_CACHEABLE = ClassName.get("ru.tinkoff.kora.cache.annotation", "Cacheable");
    private static final ClassName ANNOTATION_CACHEABLES = ClassName.get("ru.tinkoff.kora.cache.annotation", "Cacheables");

    private final ProcessingEnvironment env;

    public CacheableAopKoraAspect(ProcessingEnvironment env) {
        this.env = env;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(ANNOTATION_CACHEABLE.canonicalName(), ANNOTATION_CACHEABLES.canonicalName());
    }

    @Override
    public ApplyResult apply(ExecutableElement method, String superCall, AspectContext aspectContext) {
        if (MethodUtils.isFuture(method)) {
            throw new ProcessingErrorException("@Cacheable can't be applied for types assignable from " + Future.class, method);
        } else if (MethodUtils.isFlux(method)) {
            throw new ProcessingErrorException("@Cacheable can't be applied for types assignable from " + Flux.class, method);
        }

        final CacheOperation operation = CacheOperationUtils.getCacheMeta(method);
        final List<String> cacheFields = getCacheFields(operation, env, aspectContext);

        final CodeBlock body = MethodUtils.isMono(method)
            ? buildBodyMono(method, operation, cacheFields, superCall)
            : buildBodySync(method, operation, cacheFields, superCall);

        return new ApplyResult.MethodBody(body);
    }

    private CodeBlock buildBodySync(ExecutableElement method,
                                    CacheOperation operation,
                                    List<String> cacheFields,
                                    String superCall) {
        final String superMethod = getSuperMethod(method, superCall);

        final CodeBlock keyBlock;
        if (operation.parameters().size() == 1) {
            keyBlock = CodeBlock.builder()
                .add("""
                        var _key = $L;
                        """,
                    operation.parameters().get(0))
                .build();
        } else {
            final String recordParameters = getKeyRecordParameters(operation, method);
            keyBlock = CodeBlock.builder()
                .add("""
                        var _key = $T.of($L);
                        """,
                    getCacheKey(operation), recordParameters)
                .build();

        }

        final boolean isOptional = MethodUtils.isOptional(method);
        if (operation.cacheImplementations().size() == 1) {
            if (isOptional) {
                return CodeBlock.builder()
                    .add(keyBlock)
                    .add(CodeBlock.of("""
                        return $T.ofNullable($L.computeIfAbsent(_key, _k -> $L.orElse(null)));
                        """, Optional.class, cacheFields.get(0), superMethod))
                    .build();
            } else {
                return CodeBlock.builder()
                    .add(keyBlock)
                    .add(CodeBlock.of("""
                        return $L.computeIfAbsent(_key, _k -> $L);
                        """, cacheFields.get(0), superMethod))
                    .build();
            }
        }

        final CodeBlock.Builder builder = CodeBlock.builder();

        // cache get
        for (int i = 0; i < cacheFields.size(); i++) {
            final String cache = cacheFields.get(i);
            final String prefix = (i == 0)
                ? "var _value = "
                : "_value = ";

            builder.add(prefix).add(cache).add(".get(_key);\n");

            builder.beginControlFlow("if(_value != null)");
            // put value from cache into prev level caches
            for (int j = 0; j < i; j++) {
                final String cachePrevPut = cacheFields.get(j);
                builder.add("\t").add(cachePrevPut).add(".put(_key, _value);\n");
            }

            if(isOptional) {
                builder.add("return $T.of(_value);", Optional.class);
            } else {
                builder.add("return _value;");
            }

            builder.add("\n");
            builder.endControlFlow();
            builder.add("\n");
        }

        // cache super method
        builder.add("var _result = ").add(superMethod).add(";\n");

        // cache put
        final boolean isPrimitive = method.getReturnType() instanceof PrimitiveType;
        if(isOptional) {
            builder.beginControlFlow("_result.ifPresent(_v ->");
        } else if(!isPrimitive) {
            builder.beginControlFlow("if(_result != null)");
        }

        for (final String cache : cacheFields) {
            if (isOptional) {
                builder.add(cache).add(".put(_key, _v);\n");
            } else {
                builder.add(cache).add(".put(_key, _result);\n");
            }
        }

        if(isOptional) {
            builder.endControlFlow(")");
        } else if(!isPrimitive) {
            builder.endControlFlow();
        }

        builder.add("return _result;");

        return CodeBlock.builder()
            .add(keyBlock)
            .add(builder.build())
            .build();
    }

    private CodeBlock buildBodyMono(ExecutableElement method,
                                    CacheOperation operation,
                                    List<String> cacheFields,
                                    String superCall) {
        final String superMethod = getSuperMethod(method, superCall);

        // cache variables
        final CodeBlock.Builder builder = CodeBlock.builder();

        // cache get
        for (int i = 0; i < cacheFields.size(); i++) {
            final String cache = cacheFields.get(i);
            final String prefix = (i == 0)
                ? "var _value = "
                : "_value = _value.switchIfEmpty(";

            final String getPart = ".getAsync(_key)";
            builder.add(prefix)
                .add(cache)
                .add(getPart);

            // put value from cache into prev level caches
            if (i > 1) {
                builder.add("\n").add("""
                        .publishOn($T.boundedElastic())
                        .doOnSuccess(_fromCache -> {
                            if(_fromCache != null) {
                                $T.merge($T.of(
                    """, Schedulers.class, Flux.class, List.class);

                for (int j = 0; j < i; j++) {
                    final String prevCache = cacheFields.get(j);
                    final String suffix = (j == i - 1)
                        ? ".putAsync(_key, _fromCache)\n"
                        : ".putAsync(_key, _fromCache),\n";
                    builder.add("\t\t\t\t").add(prevCache).add(suffix);
                }

                builder.add("\t\t)).then().block();\n}}));\n\n");
            } else if (i == 1) {
                builder.add("\n\t")
                    .add(String.format("""
                        .doOnSuccess(_fromCache -> {
                                if(_fromCache != null) {
                                    %s.put(_key, _fromCache);
                                }
                        }));
                        """, cacheFields.get(0)))
                    .add("\n");
            } else {
                builder.add(";\n");
            }
        }

        // cache super method
        builder.add("return _value.switchIfEmpty(").add(superMethod);

        // cache put
        if (cacheFields.size() > 1) {
            builder.add(".flatMap(_result -> $T.merge($T.of(\n", Flux.class, List.class);
            for (int i = 0; i < cacheFields.size(); i++) {
                final String cache = cacheFields.get(i);
                final String suffix = (i == cacheFields.size() - 1)
                    ? ".putAsync(_key, _result)\n"
                    : ".putAsync(_key, _result),\n";
                builder.add("\t").add(cache).add(suffix);
            }
            builder.add(")).then(Mono.just(_result))));");
        } else {
            if (operation.parameters().size() == 1) {
                builder.add("""
                    .doOnSuccess(_result -> {
                        if(_result != null) {
                            $L.put($L, _result);
                        }
                    }));
                    """, cacheFields.get(0), operation.parameters().get(0));
            } else {
                final String recordParameters = getKeyRecordParameters(operation, method);
                builder.add("""
                    .doOnSuccess(_result -> {
                        if(_result != null) {
                            $L.put($T.of($L), _result);
                        }
                    }));
                    """, cacheFields.get(0), getCacheKey(operation), recordParameters);
            }
        }

        if (operation.parameters().size() == 1) {
            return CodeBlock.builder()
                .add("""
                        var _key = $L;
                        """,
                    operation.parameters().get(0))
                .add(builder.build())
                .build();
        } else {
            final String recordParameters = getKeyRecordParameters(operation, method);
            return CodeBlock.builder()
                .add("""
                        var _key = $T.of($L);
                        """,
                    getCacheKey(operation), recordParameters)
                .add(builder.build())
                .build();

        }
    }
}
