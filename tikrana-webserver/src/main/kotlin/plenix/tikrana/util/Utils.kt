package plenix.tikrana.util

fun loadResource(resourceName: String) =
    Thread.currentThread().contextClassLoader.getResourceAsStream(resourceName)!!
