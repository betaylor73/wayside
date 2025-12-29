package com.questrail.wayside.protocol.genisys.internal.decode;

/**
 * Indicates that a decoded GENISYS frame could not be translated into
 * a valid semantic GENISYS message.
 *
 * This typically reflects:
 * <ul>
 *   <li>Unknown or unsupported header byte</li>
 *   <li>Illegal payload shape for the message type</li>
 *   <li>Failure in delegated payload decoding</li>
 * </ul>
 */
public final class GenisysDecodeException extends RuntimeException
{
    public GenisysDecodeException(String message) {
        super(message);
    }

    public GenisysDecodeException(String message, Throwable cause) {
        super(message, cause);
    }
}
