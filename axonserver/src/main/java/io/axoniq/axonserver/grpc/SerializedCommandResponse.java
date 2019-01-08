package io.axoniq.axonserver.grpc;

import com.google.protobuf.AbstractParser;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import io.axoniq.axonserver.grpc.command.CommandResponse;

import java.io.IOException;

/**
 * Author: marc
 */
public class SerializedCommandResponse extends SerializedMessage<CommandResponse> {

    private volatile String requestIdentifier;
    private volatile CommandResponse wrapped;
    private final byte[] serializedData;

    public SerializedCommandResponse(CommandResponse response) {
        serializedData = response.toByteArray();
        wrapped = response;
    }

    public SerializedCommandResponse(byte[] readByteArray) {
        serializedData = readByteArray;
    }

    public SerializedCommandResponse(String requestIdentifier, byte[] serializedData) {
        this.requestIdentifier = requestIdentifier;
        this.serializedData = serializedData;
    }


    public static SerializedCommandResponse getDefaultInstance() {
        return new SerializedCommandResponse(CommandResponse.getDefaultInstance());
    }

    @Override
    public CommandResponse wrapped() {
        if( wrapped == null) {
            try {
                wrapped = CommandResponse.parseFrom(serializedData);
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
            }
        }
        return wrapped;
    }

    @Override
    public void writeTo(CodedOutputStream output) throws IOException {
        output.write(serializedData, 0, serializedData.length);
    }

    @Override
    public int getSerializedSize() {
        return serializedData.length;
    }

    @Override
    public byte[] toByteArray() {
        return serializedData;
    }

    @Override
    public Parser<? extends Message> getParserForType() {
        return new AbstractParser<Message>() {
            @Override
            public Message parsePartialFrom(CodedInputStream codedInputStream,
                                            ExtensionRegistryLite extensionRegistryLite)
                    throws InvalidProtocolBufferException {
                try {
                    return new SerializedCommandResponse(codedInputStream.readByteArray());
                } catch (IOException e) {
                    throw new InvalidProtocolBufferException(e);
                }
            }
        };
    }

    @Override
    public Message.Builder newBuilderForType() {
        return new Builder();
    }

    @Override
    public Message.Builder toBuilder() {
        return new Builder().setCommandResponse(wrapped);
    }

    @Override
    public Message getDefaultInstanceForType() {
        return getDefaultInstance();
    }

    public String getRequestIdentifier() {
        if( requestIdentifier != null) return requestIdentifier;
        return wrapped().getRequestIdentifier();
    }

    @Override
    public ByteString toByteString() {
        return ByteString.copyFrom(serializedData);
    }

    public String getErrorCode() {
        return wrapped().getErrorCode();
    }

    public static class Builder extends GeneratedMessageV3.Builder<Builder> {
        private CommandResponse commandResponse;

        @Override
        protected GeneratedMessageV3.FieldAccessorTable internalGetFieldAccessorTable() {
            return null;
        }

        @Override
        public Message build() {
            return new SerializedCommandResponse(commandResponse);
        }

        @Override
        public Message buildPartial() {
            return new SerializedCommandResponse(commandResponse);
        }

        @Override
        public Message getDefaultInstanceForType() {
            return getDefaultInstance();
        }

        public Builder setCommandResponse(CommandResponse commandResponse) {
            this.commandResponse = commandResponse;
            return this;
        }
    }
}
