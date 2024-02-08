package de.tracetronic.jenkins.plugins.ecutestexecution.clients.model

/**
 * Abstraction of ecu.test API exceptions for all the different api versions.
 */
class ApiException extends Exception {
    ApiException(String message) {
        super (message);
    }

    ApiException(String message, Throwable cause) {
        super (message, cause);
    }
}
