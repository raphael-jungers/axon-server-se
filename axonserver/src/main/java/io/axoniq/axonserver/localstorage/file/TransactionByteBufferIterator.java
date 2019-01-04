package io.axoniq.axonserver.localstorage.file;

import io.axoniq.axonserver.exception.ErrorCode;
import io.axoniq.axonserver.exception.MessagingPlatformException;
import io.axoniq.axonserver.grpc.internal.TransactionWithToken;
import io.axoniq.axonserver.localstorage.SerializedEvent;
import io.axoniq.axonserver.localstorage.SerializedTransactionWithToken;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Author: marc
 */
public class TransactionByteBufferIterator implements TransactionIterator {

    private final ByteBuffer reader;
    private final ByteBufferEventSource eventSource;
    private long currentSequenceNumber;
    private final boolean validating;
    private SerializedTransactionWithToken next;


    public TransactionByteBufferIterator(ByteBufferEventSource eventSource, long segment, long token, boolean validating) {
        this.eventSource = eventSource;
        this.reader = eventSource.getBuffer();
        this.currentSequenceNumber = segment;
        this.validating = validating;
        forwardTo(token);
    }

    private void forwardTo(long firstSequence) {
        reader.position(5);
        while (firstSequence > currentSequenceNumber) {

            int size = reader.getInt();
            if (size == -1 || size == 0) {
                return;
            }
            reader.get(); // version
            short nrOfMessages = reader.getShort();

            if (firstSequence >= currentSequenceNumber + nrOfMessages) {
                reader.position(reader.position() + size + 4);
                currentSequenceNumber += nrOfMessages;
            } else {
                throw new MessagingPlatformException(ErrorCode.INVALID_TRANSACTION_TOKEN,
                        "FirstSequence in middle of transaction, firstSequence=" + firstSequence + ", current="
                                + currentSequenceNumber + ", nrOfMessages=" + nrOfMessages);
            }
        }
    }

    private boolean readTransaction() {
        int size = reader.getInt();
        if (size == -1 || size == 0) {
            reader.position(reader.position()-4);
            return false;
        }
        byte version = reader.get(); // version

        short nrOfMessages = reader.getShort();
        List<SerializedEvent> events = new ArrayList<>(nrOfMessages);
        int position = reader.position();
        for (int idx = 0; idx < nrOfMessages; idx++) {
            events.add(eventSource.readEvent());
        }
        next = new SerializedTransactionWithToken(currentSequenceNumber, version, events);
        currentSequenceNumber += nrOfMessages;
        int chk = reader.getInt(); // checksum
        if (validating) {
            Checksum checksum = new Checksum();
            checksum.update(reader, position, size);
            if( chk != checksum.get()) {
                throw new MessagingPlatformException(ErrorCode.VALIDATION_FAILED, "Invalid checksum at " + currentSequenceNumber);
            }
        }
        return true;
    }

    @Override
    public boolean hasNext() {
        return readTransaction();
    }

    @Override
    public SerializedTransactionWithToken next() {
        if( next == null) throw new NoSuchElementException();
        return next;
    }

    @Override
    public void close() {
        eventSource.close();
    }

}
