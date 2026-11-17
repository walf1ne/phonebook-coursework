package ru.course.phonebook;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32;

public final class BinaryContactRepository implements ContactRepository, AutoCloseable {
    private static final int MAGIC = 0x50424B31;
    private static final int VERSION = 1;
    private static final int MAX_CONTACTS = 10000;
    private static final int MAX_BYTES = 32 * 1024 * 1024;
    private final Path path;
    private final FileChannel lockChannel;
    private final FileLock lock;

    public BinaryContactRepository(Path path) throws IOException {
        this.path = path.toAbsolutePath().normalize();
        Files.createDirectories(this.path.getParent());
        lockChannel = FileChannel.open(Path.of(this.path + ".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            lock = lockChannel.tryLock();
            if (lock == null) {
                throw new IOException("База уже открыта в другом экземпляре программы");
            }
        } catch (IOException | OverlappingFileLockException exception) {
            lockChannel.close();
            throw new IOException("База уже открыта или недоступна для блокировки", exception);
        }
    }

    @Override
    public List<Contact> load() throws IOException {
        if (!Files.exists(path)) {
            return List.of();
        }
        long size = Files.size(path);
        if (size < 20 || size > MAX_BYTES) {
            throw new IOException("Недопустимый размер файла базы");
        }
        byte[] bytes = Files.readAllBytes(path);
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                throw new IOException("Неизвестный формат или версия базы");
            }
            int length = input.readInt();
            if (length < 4 || length != bytes.length - 20) {
                throw new IOException("Повреждена длина данных");
            }
            byte[] payload = input.readNBytes(length);
            CRC32 crc = new CRC32();
            crc.update(payload);
            if (input.readLong() != crc.getValue()) {
                throw new IOException("Нарушена контрольная сумма базы");
            }
            return decode(payload);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IOException("В базе обнаружены некорректные записи", exception);
        }
    }

    private List<Contact> decode(byte[] payload) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            int count = readCount(input, 0, MAX_CONTACTS);
            List<Contact> contacts = new ArrayList<>(count);
            Set<UUID> identifiers = new HashSet<>();
            for (int i = 0; i < count; i++) {
                Contact contact = readContact(input);
                if (!identifiers.add(contact.id())) {
                    throw new IOException("Повторяется идентификатор абонента");
                }
                contacts.add(contact);
            }
            if (input.available() != 0) {
                throw new IOException("Обнаружены лишние данные в базе");
            }
            return List.copyOf(contacts);
        }
    }

    private Contact readContact(DataInputStream input) throws IOException {
        UUID id = new UUID(input.readLong(), input.readLong());
        String name = input.readUTF();
        int count = readCount(input, 1, 20);
        List<PhoneNumber> phones = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            phones.add(new PhoneNumber(input.readUTF(), PhoneType.valueOf(input.readUTF())));
        }
        return new Contact(id, name, phones);
    }

    private int readCount(DataInputStream input, int minimum, int maximum) throws IOException {
        int count = input.readInt();
        if (count < minimum || count > maximum) {
            throw new IOException("Недопустимое количество записей");
        }
        return count;
    }

    @Override
    public void save(List<Contact> contacts) throws IOException {
        if (contacts.size() > MAX_CONTACTS || contacts.stream().map(Contact::id).distinct().count() != contacts.size()) {
            throw new IOException("Слишком много записей или повторяются идентификаторы");
        }
        byte[] bytes = encode(contacts);
        if (bytes.length > MAX_BYTES) {
            throw new IOException("База превышает допустимый размер");
        }
        Path temporary = Files.createTempFile(path.getParent(), "phonebook-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            backupExisting();
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void backupExisting() throws IOException {
        if (Files.exists(path)) {
            load();
            Files.copy(path, Path.of(path + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private byte[] encode(List<Contact> contacts) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeInt(contacts.size());
            for (Contact contact : contacts) {
                writeContact(output, contact);
            }
        }
        byte[] payload = buffer.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(payload);
        buffer.reset();
        try (DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeInt(MAGIC);
            output.writeInt(VERSION);
            output.writeInt(payload.length);
            output.write(payload);
            output.writeLong(crc.getValue());
        }
        return buffer.toByteArray();
    }

    private void writeContact(DataOutputStream output, Contact contact) throws IOException {
        output.writeLong(contact.id().getMostSignificantBits());
        output.writeLong(contact.id().getLeastSignificantBits());
        output.writeUTF(contact.fullName());
        output.writeInt(contact.phones().size());
        for (PhoneNumber phone : contact.phones()) {
            output.writeUTF(phone.number());
            output.writeUTF(phone.type().name());
        }
    }

    @Override
    public void close() throws IOException {
        try {
            lock.release();
        } finally {
            lockChannel.close();
        }
    }
}
