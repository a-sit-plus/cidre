package at.asitplus.cidre

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class ImplementationError(errorCode: String, cause: Throwable?=null) : RuntimeException(
    "Implementation error ${errorCode}. Report this bug (preferably including the full stack trace) here: https://github.com/a-sit-plus/cidre/issues/new?title=Bug+$errorCode&labels=bug",
    cause
)

@OptIn(ExperimentalContracts::class)
internal inline fun assert(condition: Boolean, errorCode: String, cause: Throwable? = null) {
    contract { returns() implies condition }
    if (!condition) throw ImplementationError(errorCode, cause)
}