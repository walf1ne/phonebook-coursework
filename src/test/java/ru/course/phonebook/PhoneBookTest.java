package ru.course.phonebook;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32;
import static org.junit.jupiter.api.Assertions.*;

class PhoneBookTest {
    @TempDir Path directory;

    private Contact contact(String name, String number) {
        return Contact.create(name, List.of(new PhoneNumber(number, PhoneType.MOBILE)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1234", "abc12345", "+1234567890123456", "123+456", "12\n345"})
    void rejectsInvalidPhones(String number) {
        assertThrows(IllegalArgumentException.class, () -> new PhoneNumber(number, PhoneType.HOME));
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "+7 (900) 123-45-67", "123456789012345"})
    void acceptsValidPhones(String number) {
        assertEquals(number, new PhoneNumber(number, PhoneType.FAX).number());
    }

    @Test
    void normalizesName() {
        assertEquals("Иванов Иван Иванович", contact("  Иванов   Иван   Иванович  ", "12345").fullName());
    }

    @Test
    void rejectsMissingNameAndPhones() {
        assertThrows(IllegalArgumentException.class, () -> contact(" ", "12345"));
        assertThrows(IllegalArgumentException.class, () -> Contact.create("Иванов Иван Иванович", List.of()));
        assertThrows(IllegalArgumentException.class, () -> contact("я".repeat(151), "12345"));
    }

    @Test
    void rejectsDuplicatePhonesIgnoringFormatting() {
        assertThrows(IllegalArgumentException.class, () -> Contact.create("Иванов Иван Иванович", List.of(
                new PhoneNumber("12-345", PhoneType.HOME), new PhoneNumber("12345", PhoneType.WORK))));
    }

    @Test
    void contactOwnsImmutablePhoneList() {
        List<PhoneNumber> phones = new ArrayList<>(List.of(new PhoneNumber("12345", PhoneType.FAX)));
        Contact contact = Contact.create("Иванов Иван Иванович", phones);
        phones.clear();
        assertEquals(1, contact.phones().size());
        assertThrows(UnsupportedOperationException.class, () -> contact.phones().clear());
    }

    @Test
    void binaryRoundTripPreservesMultipleTypesAndUnicode() throws Exception {
        Path path = directory.resolve("contacts.pbk");
        Contact sample = Contact.create("Семёнов Артём Семёнович", List.of(
                new PhoneNumber("+7 (900) 123-45-67", PhoneType.MOBILE),
                new PhoneNumber("812-555-12-34", PhoneType.FAX)));
        try (var repository = new BinaryContactRepository(path)) {
            assertTrue(repository.load().isEmpty());
            repository.save(List.of(sample));
        }
        try (var reopened = new BinaryContactRepository(path)) {
            assertEquals(List.of(sample), reopened.load());
        }
    }

    @Test
    void supportsEmptyDatabase() throws Exception {
        try (var repository = new BinaryContactRepository(directory.resolve("empty.pbk"))) {
            repository.save(List.of());
            assertTrue(repository.load().isEmpty());
        }
    }

    @Test
    void savesPreviousVersionAsBackup() throws Exception {
        Path path = directory.resolve("contacts.pbk");
        try (var repository = new BinaryContactRepository(path)) {
            repository.save(List.of(contact("Иванов Иван Иванович", "12345")));
            byte[] first = Files.readAllBytes(path);
            repository.save(List.of(contact("Петров Пётр Петрович", "54321")));
            assertArrayEquals(first, Files.readAllBytes(Path.of(path + ".bak")));
        }
    }

    @Test
    void rejectsCorruptionWithoutOverwritingOriginal() throws Exception {
        Path path = directory.resolve("bad.pbk");
        try (var repository = new BinaryContactRepository(path)) {
            repository.save(List.of(contact("Иванов Иван Иванович", "12345")));
            byte[] bytes = Files.readAllBytes(path);
            bytes[16] ^= 1;
            Files.write(path, bytes);
            assertThrows(IOException.class, repository::load);
            assertThrows(IOException.class, () -> repository.save(List.of()));
            assertArrayEquals(bytes, Files.readAllBytes(path));
        }
    }

    @Test
    void rejectsTruncatedFile() throws Exception {
        Path path = directory.resolve("short.pbk");
        Files.write(path, new byte[]{1, 2, 3});
        try (var repository = new BinaryContactRepository(path)) {
            assertThrows(IOException.class, repository::load);
        }
    }

    @Test
    void rejectsWrongVersion() throws Exception {
        Path path = directory.resolve("version.pbk");
        try (var repository = new BinaryContactRepository(path)) {
            repository.save(List.of());
            byte[] bytes = Files.readAllBytes(path);
            bytes[7] = 2;
            Files.write(path, bytes);
            assertThrows(IOException.class, repository::load);
        }
    }

    @Test
    void rejectsInvalidCountWithValidChecksum() throws Exception {
        Path path = directory.resolve("count.pbk");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] payload = {-1, -1, -1, -1};
        CRC32 crc = new CRC32();
        crc.update(payload);
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeInt(0x50424B31);
            out.writeInt(1);
            out.writeInt(4);
            out.write(payload);
            out.writeLong(crc.getValue());
        }
        Files.write(path, buffer.toByteArray());
        try (var repository = new BinaryContactRepository(path)) {
            assertThrows(IOException.class, repository::load);
        }
    }

    @Test
    void preventsConcurrentOpenAndReleasesLock() throws Exception {
        Path path = directory.resolve("locked.pbk");
        try (var first = new BinaryContactRepository(path)) {
            assertThrows(IOException.class, () -> new BinaryContactRepository(path));
        }
        try (var next = new BinaryContactRepository(path)) {
            assertTrue(next.load().isEmpty());
        }
    }

    @Test
    void searchesNameAndFormattedNumberAndSortsRussianNames() throws Exception {
        try (var repository = new BinaryContactRepository(directory.resolve("search.pbk"))) {
            ContactService service = new ContactService(repository);
            service.upsert(contact("Яковлев Ян Яковлевич", "+7 (900) 123-45-67"));
            service.upsert(contact("Антонов Антон Антонович", "8125551234"));
            assertEquals("Антонов Антон Антонович", service.search("", false).get(0).fullName());
            assertEquals("Яковлев Ян Яковлевич", service.search("", true).get(0).fullName());
            assertEquals(1, service.search("яКОВ", false).size());
            assertEquals(1, service.search("900 123", false).size());
            assertTrue(service.search("---", false).isEmpty());
            assertTrue(service.search("абв123", false).isEmpty());
            assertTrue(service.search("нет совпадений", false).isEmpty());
        }
    }

    @Test
    void updateRetainsIdentityAndDeletePersists() throws Exception {
        try (var repository = new BinaryContactRepository(directory.resolve("edit.pbk"))) {
            ContactService service = new ContactService(repository);
            Contact initial = contact("Иванов Иван Иванович", "12345");
            service.upsert(initial);
            service.upsert(new Contact(initial.id(), "Петров Пётр Петрович", initial.phones()));
            assertEquals(1, service.size());
            assertEquals("Петров Пётр Петрович", repository.load().get(0).fullName());
            service.delete(initial.id());
            assertTrue(repository.load().isEmpty());
            assertThrows(IllegalArgumentException.class, () -> service.delete(initial.id()));
        }
    }

    @Test
    void failedWriteDoesNotChangeMemory() throws Exception {
        Contact initial = contact("Иванов Иван Иванович", "12345");
        ContactRepository failing = new ContactRepository() {
            public List<Contact> load() { return List.of(initial); }
            public void save(List<Contact> contacts) throws IOException { throw new IOException("Диск недоступен"); }
        };
        ContactService service = new ContactService(failing);
        assertThrows(IOException.class, () -> service.upsert(contact("Петров Пётр Петрович", "54321")));
        assertEquals(List.of(initial), service.search("", false));
        assertThrows(IOException.class, () -> service.delete(initial.id()));
        assertEquals(1, service.size());
    }

    @Test
    void rejectsDuplicateIdentifiers() throws Exception {
        try (var repository = new BinaryContactRepository(directory.resolve("duplicate.pbk"))) {
            Contact sample = contact("Иванов Иван Иванович", "12345");
            assertThrows(IOException.class, () -> repository.save(List.of(sample, sample)));
        }
    }
}
