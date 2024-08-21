package org.apache.tika.pipes.grpc.exception;

public class TikaGrpcException extends RuntimeException {
    public TikaGrpcException(String message, Throwable cause) {
        super(message, cause);
    }
}
