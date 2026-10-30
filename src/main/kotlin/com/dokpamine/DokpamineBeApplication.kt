package com.dokpamine

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class DokpamineBeApplication

fun main(args: Array<String>) {
    runApplication<DokpamineBeApplication>(*args)
}
