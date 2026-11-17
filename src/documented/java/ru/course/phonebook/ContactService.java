package ru.course.phonebook;

import java.io.IOException;
import java.text.Collator;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Выполняет поиск и изменения справочника с сохранением перед обновлением памяти.
 */
public final class ContactService {
    private static final Logger LOGGER = LogManager.getLogger(ContactService.class);
    private final ContactRepository repository;
    private List<Contact> contacts;

    /**
     * Загружает проверенные записи из выбранного хранилища.
     */
    public ContactService(ContactRepository repository) throws IOException {
        this.repository = repository;
        contacts = List.copyOf(repository.load());
        LOGGER.info("База загружена. Количество записей: {}", contacts.size());
    }

    /**
     * Фильтрует записи по ФИО или цифрам телефона и сортирует по русским правилам сравнения.
     */
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

    /**
     * Проверяет совпадение имени или номера; телефонный поиск допускается только для цифрового запроса.
     */
    private boolean matches(Contact contact, String text, String digits, boolean phoneQuery) {
        return contact.fullName().toLowerCase(Locale.ROOT).contains(text)
                || phoneQuery && contact.phones().stream()
                .anyMatch(phone -> PhoneNumber.digits(phone.number()).contains(digits));
    }

    /**
     * Добавляет запись или заменяет абонента с тем же UUID; при ошибке записи память не меняется.
     */
    public void upsert(Contact contact) throws IOException {
        List<Contact> next = new ArrayList<>(contacts);
        next.removeIf(existing -> existing.id().equals(contact.id()));
        next.add(contact);
        commit(next, "Абонент сохранён");
    }

    /**
     * Удаляет запись по UUID и сохраняет результат; неизвестный UUID отклоняется.
     */
    public void delete(UUID id) throws IOException {
        List<Contact> next = new ArrayList<>(contacts);
        if (!next.removeIf(contact -> contact.id().equals(id))) {
            throw new IllegalArgumentException("Абонент не найден");
        }
        commit(next, "Абонент удалён");
    }

    /**
     * Сначала сохраняет новый список, затем обновляет состояние и журналирует результат без персональных данных.
     */
    private void commit(List<Contact> next, String operation) throws IOException {
        repository.save(next);
        contacts = List.copyOf(next);
        LOGGER.info("{}. Количество записей: {}", operation, contacts.size());
    }

    /**
     * Возвращает количество абонентов в текущем справочнике.
     */
    public int size() {
        return contacts.size();
    }
}
