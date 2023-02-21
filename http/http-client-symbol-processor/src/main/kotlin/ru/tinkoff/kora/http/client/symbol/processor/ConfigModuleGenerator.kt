package ru.tinkoff.kora.http.client.symbol.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.typesafe.config.Config
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor
import ru.tinkoff.kora.http.client.common.annotation.HttpClient
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated

class ConfigModuleGenerator(val resolver: Resolver) {

    @KspExperimental
    fun generate(declaration: KSClassDeclaration): FileSpec {
        val lowercaseName = StringBuilder(declaration.simpleName.asString())
        lowercaseName.setCharAt(0, lowercaseName[0].lowercaseChar())
        val packageName = declaration.packageName.asString()
        var configPath: String = declaration.getAnnotationsByType(
            HttpClient::class
        ).first().configPath
        if (configPath.isBlank()) {
            configPath = "httpClient.$lowercaseName"
        }
        val configName = declaration.configName()
        val moduleName = declaration.moduleName()
        val configClass = ClassName(packageName, configName)
        val extractorClass = ConfigValueExtractor::class.asClassName().parameterizedBy(configClass)
        val type = TypeSpec.interfaceBuilder(moduleName)
            .generated(ConfigModuleGenerator::class)
            .addAnnotation(AnnotationSpec.builder(CommonClassNames.module).build())
            .addOriginatingKSFile(declaration.containingFile!!)
            .addFunction(
                FunSpec.builder(lowercaseName.toString() + "Config")
                    .returns(configClass)
                    .addParameter(ParameterSpec.builder("config", Config::class).build())
                    .addParameter(ParameterSpec.builder("extractor", extractorClass).build())
                    .addStatement("val value = config.getValue(%S)", configPath)
                    .addStatement("return extractor.extract(value)")
                    .build()
            )
        return FileSpec.builder(packageName, moduleName)
            .addType(type.build())
            .build()
    }
}
