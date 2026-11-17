package ru.course.phonebook;

import java.io.IOException;
import java.text.Collator;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ContactService {
    private static final Logger LOGGER = LogManager.getLogger(ContactService.class);
    private final ContactRepository repository;
    private List<Contact> contacts;

    public ContactService(ContactRepository repository) throws IOException {
        this.repository = repository;
        contacts = List.copyOf(repository.load());
        LOGGER.info("База загружена. Количество записей: {}", contacts.size());
    }

    public List<Contact> search(String query, boolean descending) {
        String text = query.strip().toLowerCase(Locale.ROOT);
        String digits = PhoneNumber.digits(text);
        boolean phoneQuery = text.matches("\\+?[0-9 ()-]+") && !digits.isEmpty();
        Collator collator = Collator.getInstance(Locale.forLanguageTag("ru"));
        Comparator<Contact> comparator = Comparator.comparing(Contact::fullName, collator)
                .thenComparing(contact -> contact.id().toString());
        return contacts.stream()
                .filter(contact -> matches(contact, text, digits, phoneQuery))
                .sorted(descending ? comparator.reversed() : comparator)
                .toList();
    }

    private boolean matches(Contact contact, String text, String digits, boolean phoneQuery) {
        return contact.fullName().toLowerCase(Locale.ROOT).contains(text)
                || phoneQuery && contact.phones().stream()
                .anyMatch(phone -> PhoneNumber.digits(phone.number()).contains(digits));
    }

    public void upsert(Contact contact) throws IOException {
        List<Contact> next = new ArrayList<>(contacts);
        next.removeIf(existing -> existing.id().equals(contact.id()));
        next.add(contact);
        commit(next, "Абонент сохранён");
    }

    public void delete(UUID id) throws IOException {
        List<Contact> next = new ArrayList<>(contacts);
        if (!next.removeIf(contact -> contact.id().equals(id))) {
            throw new IllegalArgumentException("Абонент не найден");
        }
        commit(next, "Абонент удалён");
    }

    private void commit(List<Contact> next, String operation) throws IOException {
        repository.save(next);
        contacts = List.copyOf(next);
        LOGGER.info("{}. Количество записей: {}", operation, contacts.size());
    }

    public int size() {
        return contacts.size();
    }
}
