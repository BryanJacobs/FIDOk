package us.q3q.fidok.ctap

/**
 * Represents an error communicating with a device. Not a CTAP error; an error in the
 * transport layer underneath CTAP.
 *
 * @param message String describing the type of exception that occurred
 * @param cause Another exception that led to this one
 */
open class DeviceCommunicationException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

open class IncorrectDataException(message: String? = null, cause: Throwable? = null) :
    DeviceCommunicationException(message, cause)

open class OutOfBandErrorResponseException(message: String? = null, cause: Throwable? = null, val code: Int) :
    IncorrectDataException(message, cause)

open class InvalidDeviceException(message: String? = null, cause: Throwable? = null) :
    DeviceCommunicationException(message, cause)
