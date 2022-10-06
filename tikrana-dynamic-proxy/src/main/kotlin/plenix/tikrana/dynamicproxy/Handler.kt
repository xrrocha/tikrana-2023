package plenix.tikrana.dynamicproxy

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodHandles.Lookup
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.WeakHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.allSuperclasses
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.javaSetter
import kotlin.reflect.jvm.kotlinFunction

inline fun <reified T : Any> new(noinline block: T.() -> Unit): T =
    (new(T::class) as T).also(block)

fun new(kClass: KClass<*>): Any = Proxy.newProxyInstance(kClass.java.classLoader, arrayOf(kClass.java), Handler(kClass))

interface PropertyValueProvider<T> {
    fun provideValues(): List<Pair<KProperty1<T, *>, Any?>>
}

class Handler(kClass: KClass<*>) : InvocationHandler {

    private val values = mutableMapOf<String, Any?>()

    init {
        require(kClass.java.isInterface) { "Can't proxy non-interface class ${kClass.qualifiedName}!" }

        kClass
            .allSuperclasses
            .mapNotNull(KClass<*>::companionObjectInstance)
            .filterIsInstance(PropertyValueProvider::class.java)
            .forEach { provider ->
                provider.provideValues().forEach { (property, value) ->
                    setProperty(property, value)
                }
            }
    }

    override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? =
        when (method.declaringClass.kotlin) {
            Any::class -> {
                when (method.name) {
                    "equals" -> this == args!![0]
                    "hashCode" -> hashCode()
                    "toString" -> toString()
                    else -> throw IllegalStateException("Unrecognized method: $method")
                }
            }

            else -> {
                handleMethodCall(proxy, method, args)
            }
        }

    private fun handleMethodCall(proxy: Any, method: Method, args: Array<out Any>?): Any? {
        val propertyPair = method.kotlinProperty
        if (propertyPair != null) {
            val (property, isGetter) = propertyPair
            return if (property.isAbstract) {
                if (isGetter) {
                    val result = getProperty(property)
                    if (result != null || property.returnType.isMarkedNullable) {
                        result
                    } else {
                        property.defaultValue.also { cacheDefaultValue(property, it) }
                    }
                } else {
                    setProperty(property, args!![0])
                    null
                }
            } else {
                MethodHandler.forMethod(method).invoke(proxy, args)
            }
        } else {
            val kotlinFunction = method.kotlinFunction
            if (!(kotlinFunction == null || kotlinFunction.isAbstract)) {
                return MethodHandler.forMethod(method).invoke(proxy, args)
            } else {
                throw IllegalStateException("Can't invoke abstract method: $method")
            }
        }
    }

    private fun getProperty(prop: KProperty1<*, *>): Any? =
        values[prop.name]?.unboxTo(prop.javaGetter!!.returnType)

    private fun setProperty(prop: KProperty1<*, *>, value: Any?) {
        values[prop.name] = value
    }

    private fun cacheDefaultValue(prop: KProperty1<*, *>, value: Any) {
        val type = prop.javaGetter!!.returnType
        if (!(type.isEnum || primitives.contains(type))) {
            setProperty(prop, value)
        }
    }

    companion object {
        val primitives = setOf(
            Boolean::class, Byte::class, Char::class, Double::class,
            Float::class, Int::class, Long::class, Short::class, String::class,
            UByte::class, UInt::class, ULong::class, UShort::class,
        )
            .map { it.javaPrimitiveType }
    }
}

internal class MethodHandler(val invoker: (Any, Array<out Any>?) -> Any?) {

    fun invoke(proxy: Any, args: Array<out Any>?): Any? = invoker(proxy, args)

    companion object {
        private val handlersCache = Collections.synchronizedMap(WeakHashMap<Method, MethodHandler>())
        private val privateLookupIn =
            MethodHandles::class.java.getMethod("privateLookupIn", Class::class.java, Lookup::class.java)

        fun forMethod(method: Method): MethodHandler =
            handlersCache.computeIfAbsent(method) {
                if (method.isDefault) {
                    unreflectSpecial(method).let { methodHandle ->
                        MethodHandler { proxy, args ->
                            if (args == null) {
                                methodHandle.bindTo(proxy).invokeWithArguments()
                            } else {
                                methodHandle.bindTo(proxy).invokeWithArguments(*args)
                            }
                        }
                    }
                } else {
                    Class.forName(method.declaringClass.name + "\$DefaultImpls")
                        .getMethod(method.name, method.declaringClass, *method.parameterTypes)
                        .let { methodImplementation ->
                            MethodHandler { proxy, args ->
                                if (args == null) {
                                    methodImplementation.doInvoke(null, proxy)
                                } else {
                                    methodImplementation.doInvoke(null, proxy, *args)
                                }
                            }
                        }
                }
            }

        private fun unreflectSpecial(method: Method): MethodHandle =
            (privateLookupIn.doInvoke(null, method.declaringClass, MethodHandles.lookup()) as Lookup)
                .unreflectSpecial(method, method.declaringClass)
    }
}

internal val Method.kotlinProperty: Pair<KProperty1<*, *>, Boolean>?
    get() {
        for (prop in declaringClass.kotlin.declaredMemberProperties) {
            if (prop.javaGetter == this) {
                return Pair(prop, true)
            }
            if (prop is KMutableProperty<*> && prop.javaSetter == this) {
                return Pair(prop, false)
            }
        }
        return null
    }

internal fun Method.doInvoke(obj: Any?, vararg args: Any?): Any? {
    try {
        return invoke(obj, *args)
    } catch (e: InvocationTargetException) {
        throw e.targetException
    }
}

internal val KProperty1<*, *>.defaultValue: Any
    get() =
        try {
            javaGetter!!.returnType.defaultValue
        } catch (e: Throwable) {
            throw IllegalStateException("No default value for non-nullable property '$name'", e)
        }

internal fun Any.unboxTo(targetClass: Class<*>): Any =
    generateSequence(this) { it.javaClass.getMethod("unbox_impl").doInvoke(it) }
        .dropWhile { it::class.isInline && it.javaClass != targetClass }
        .first()

internal val KClass<*>.isInline: Boolean get() = isValue && hasAnnotation<JvmInline>()

internal val Class<*>.defaultValue: Any
    get() = when {
        this == Boolean::class.javaPrimitiveType -> false
        this == Char::class.javaPrimitiveType -> 0.toChar()
        this == Byte::class.javaPrimitiveType -> 0.toByte()
        this == Short::class.javaPrimitiveType -> 0.toShort()
        this == Int::class.javaPrimitiveType -> 0
        this == Long::class.javaPrimitiveType -> 0L
        this == Float::class.javaPrimitiveType -> 0.0F
        this == Double::class.javaPrimitiveType -> 0.0
        this == String::class.java -> ""
        this == UByte::class.java -> 0.toUByte()
        this == UShort::class.java -> 0.toUShort()
        this == UInt::class.java -> 0U
        this == ULong::class.java -> 0UL
        this == Set::class.java -> LinkedHashSet<Any?>()
        this == List::class.java -> ArrayList<Any?>()
        this == Collection::class.java -> ArrayList<Any?>()
        this == Map::class.java -> LinkedHashMap<Any?, Any?>()
        isEnum -> enumConstants[0]
        isArray -> java.lang.reflect.Array.newInstance(componentType, 0)
        else -> kotlin.createInstance()
    }


