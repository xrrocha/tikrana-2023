package plenix.tikrana.dproxy

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodHandles.Lookup
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.Deque
import java.util.LinkedList
import java.util.NavigableMap
import java.util.NavigableSet
import java.util.Queue
import java.util.SortedMap
import java.util.SortedSet
import java.util.TreeMap
import java.util.TreeSet
import java.util.WeakHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.allSuperclasses
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.javaSetter
import kotlin.reflect.jvm.kotlinFunction

interface Initializer<T> {
    fun initialize(instance: T)
}

object DProxy {
    inline fun <reified T: Any> create(block: T.() -> Unit): T =
//        @Suppress("UNCHECKED_CAST")
        (create(T::class) as T)
            .also(block)
            .also { instance ->
                T::class
                    .allSuperclasses
                    .mapNotNull(KClass<*>::companionObjectInstance)
                    .filterIsInstance(Initializer::class.java)
                    .forEach {
                        (it as Initializer<Any>).initialize(instance) }
            }
//            .also { instance ->
//                // TODO Validate all required properties have been populated
//                T::class
//                    .allSuperclasses
//                    .flatMap(KClass<*>::memberProperties)
//                    .filterNot{ (it as KProperty1<Any>).get(instance) != null}
//            }

    fun create(klass: KClass<*>): Any =
        InvocationHandlerImpl(klass)
            .let {
                Proxy.newProxyInstance(klass.java.classLoader, arrayOf(klass.java), it)
            }
}

internal class InvocationHandlerImpl(klass: KClass<*>) : InvocationHandler {

    private var values = mutableMapOf<String, Any?>()

    init {
        require(klass.java.isInterface) { "Can't proxy non-interface class ${klass.qualifiedName}!" }
    }

    override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? =
        when (method.declaringClass.kotlin) {
            Any::class -> {
                when (method.name) {
                    "equals" -> this == args!![0]
                    "hashCode" -> this.hashCode()
                    "toString" -> this.toString()
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
                    val result = this.getProperty(property)
                    if (result != null || property.returnType.isMarkedNullable) {
                        result
                    } else {
                        property.defaultValue.also { cacheDefaultValue(property, it) }
                    }
                } else {
                    this.setProperty(property, args!![0])
                    null
                }
            } else {
                DefaultMethodHandler.forMethod(method).invoke(proxy, args)
            }
        } else {
            val kotlinFunction = method.kotlinFunction
            if (!(kotlinFunction == null || kotlinFunction.isAbstract)) {
                return DefaultMethodHandler.forMethod(method).invoke(proxy, args)
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
            Boolean::class, Char::class, Byte::class,
            Short::class, Int::class, Long::class, String::class,
            // TODO Ascertain floating point and unsigned types count as non-cached primitives
            Double::class, Float::class,
            UByte::class, UShort::class, UInt::class, ULong::class,
        )
            .map { it.javaPrimitiveType }
    }
}

internal class DefaultMethodHandler(val invoker: (Any, Array<out Any>?) -> Any?) {

    fun invoke(proxy: Any, args: Array<out Any>?): Any? = invoker(proxy, args)

    companion object {
        private val handlersCache = Collections.synchronizedMap(WeakHashMap<Method, DefaultMethodHandler>())
        private val privateLookupIn =
            MethodHandles::class.java.getMethod("privateLookupIn", Class::class.java, Lookup::class.java)

        fun forMethod(method: Method): DefaultMethodHandler =
            handlersCache.computeIfAbsent(method) {
                if (method.isDefault) {
                    unreflectSpecial(method)
                        .let { methodHandle ->
                            DefaultMethodHandler { proxy, args ->
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
                            DefaultMethodHandler { proxy, args ->
                                if (args == null) {
                                    methodImplementation.invoke0(null, proxy)
                                } else {
                                    methodImplementation.invoke0(null, proxy, *args)
                                }
                            }
                        }
                }
            }

        private fun unreflectSpecial(method: Method): MethodHandle =
            (privateLookupIn.invoke0(null, method.declaringClass, MethodHandles.lookup()) as Lookup)
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

internal fun Method.invoke0(obj: Any?, vararg args: Any?): Any? {
    try {
        return this.invoke(obj, *args)
    } catch (e: InvocationTargetException) {
        throw e.targetException
    }
}

private val KProperty1<*, *>.defaultValue: Any
    get() =
        try {
            javaGetter!!.returnType.defaultValue
        } catch (e: Throwable) {
            throw IllegalStateException("No default value for non-null property [${this.name}]", e)
        }

internal fun Any.unboxTo(targetClass: Class<*>): Any? =
    // TODO Should this be snake case unbox_impl?
    generateSequence(this) { it.javaClass.getMethod("unbox-impl").invoke0(it) }
        .dropWhile { it::class.isInline && it.javaClass != targetClass }
        .first()

internal val KClass<*>.isInline: Boolean get() = this.isValue && this.hasAnnotation<JvmInline>()

internal val Class<*>.defaultValue: Any
    get() {
        val value = when {
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
            this == Queue::class.java || this == Deque::class.java -> LinkedList<Any?>()
            this == SortedSet::class.java || this == NavigableSet::class.java -> TreeSet<Any?>()
            this == SortedMap::class.java || this == NavigableMap::class.java -> TreeMap<Any?, Any?>()
            this.isEnum -> this.enumConstants[0]
            this.isArray -> java.lang.reflect.Array.newInstance(this.componentType, 0)
            else -> this.kotlin.createInstance()
        }

        if (this.kotlin.isInstance(value)) {
            return value
        } else {
            // never happens...
            throw AssertionError("$value must be instance of $this")
        }
    }

