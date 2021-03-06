@file:JvmName("PropertyTypes")

package com.natpryce.konfig

import java.net.URI
import java.net.URISyntaxException
import java.time.*
import java.time.format.DateTimeParseException
import java.util.*

sealed class ParseResult<T> {
    class Success<T>(val value: T) : ParseResult<T>()
    class Failure<T>(val exception: Exception) : ParseResult<T>()
}

fun <T> propertyType(typeName: String, parse: (String) -> ParseResult<T>): (PropertyLocation, String) -> T {
    return { location, stringValue ->
        val parsed = parse(stringValue)
        when (parsed) {
            is ParseResult.Success<T> ->
                parsed.value
            is ParseResult.Failure<T> ->
                throw Misconfiguration(
                    "${location.source.description} ${location.nameInLocation} - invalid $typeName: $stringValue",
                    parsed.exception)
        }
    }
}

fun <T> propertyType(type: Class<T>, parse: (String) -> ParseResult<T>) = propertyType(type.simpleName, parse)

inline fun <reified T : Any> propertyType(noinline parse: (String) -> ParseResult<T>) = propertyType(T::class.java, parse)

fun <T, X : Throwable> parser(exceptionType: Class<X>, parse: (String) -> T) = fun(s: String) =
    try {
        ParseResult.Success(parse(s))
    }
    catch(e: Exception) {
        if (exceptionType.isInstance(e)) {
            ParseResult.Failure<T>(e)
        }
        else {
            throw e
        }
    }


inline fun <T, reified X : Throwable> parser(noinline parse: (String) -> T) =
    parser(X::class.java, parse)


/**
 * Wraps a [parse] function and translates [NumberFormatException]s into [Misconfiguration] exceptions.
 */
inline fun <reified T : Any> numericPropertyType(noinline parse: (String) -> T) =
    propertyType(parser<T, NumberFormatException>(parse))

/**
 * The type of string properties
 */
@JvmField
val stringType = propertyType { ParseResult.Success(it) }

/**
 * The type of Int properties
 */
@JvmField
val intType = numericPropertyType(String::toInt)

/**
 * The type of Long properties
 */
@JvmField
val longType = numericPropertyType(String::toLong)

/**
 * The type of Double properties
 */
@JvmField
val doubleType = numericPropertyType(String::toDouble)

/**
 * The type of Boolean properties
 */
@JvmField
val booleanType = propertyType { ParseResult.Success(it.toBoolean()) }

/**
 * An enumerated list of possible values, each specified by the string value used in configuration files and the
 * value used in the program.
 */
inline fun <reified T : Any> enumType(allowed: Map<String, T>) = enumType(T::class.java, allowed)

fun <T : Any> enumType(enumType: Class<T>, allowed: Map<String, T>) = propertyType(enumType) { str ->
    allowed[str]
        ?.let { ParseResult.Success(it) }
        ?: ParseResult.Failure<T>(IllegalArgumentException("invalid value: $str; must be one of: ${allowed.keys}"))
}

fun <T : Enum<T>> enumType(enumClass: Class<T>, allowed: Iterable<T>) = enumType(enumClass, allowed.associate { it.name to it })

inline fun <reified T : Enum<T>> enumType(allowed: Iterable<T>) = enumType(T::class.java, allowed.associate { it.name to it })
inline fun <reified T: Any> enumType(vararg allowed: Pair<String, T>) = enumType(mapOf(*allowed))
inline fun <reified T : Enum<T>> enumType(vararg allowed: T) = enumType(listOf(*allowed))

fun <T : Enum<T>> enumType(enumClass: java.lang.Class<T>) = enumType(enumClass, EnumSet.allOf(enumClass))
inline fun <reified T : Enum<T>> enumType() = enumType(T::class.java)

/**
 * The type of URI properties
 */
@JvmField
val uriType = propertyType(parser<URI, URISyntaxException>(::URI))


private val defaultSeparator = Regex(",\\s*")

fun <T> listType(elementType: (PropertyLocation, String) -> T, separator: Regex = defaultSeparator) =
    { p: PropertyLocation, s: String ->
        s.split(separator).map { elementAsString -> elementType(p, elementAsString) }
    }


inline fun <reified T:Any> temporalType(noinline fn: (String)->T) = propertyType(parser<T,DateTimeParseException>(fn))

@JvmField
val durationType = temporalType(Duration::parse)

@JvmField
val periodType = temporalType(Period::parse)

@JvmField
val localTimeType = temporalType(LocalTime::parse)

@JvmField
val localDateType = temporalType(LocalDate::parse)

@JvmField
val localDateTimeType = temporalType(LocalDateTime::parse)

@JvmField
val instantType = temporalType(Instant::parse)
