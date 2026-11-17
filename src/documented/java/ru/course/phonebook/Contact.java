package ru.course.phonebook;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Хранит неизменяемую запись абонента с идентификатором и телефонами.
 */
public record Contact(UUID id, String fullName, List<PhoneNumber> phones) {
    /**
     * Нормализует ФИО, проверяет количество телефонов и отсутствие повторов.
     */
    public Contact {
        Objects.requireNonNull(id, "Отсутствует идентификатор");
        Objects.requireNonNull(fullName, "Введите ФИО");
        fullName = fullName.strip().replaceAll("\\s+", " ");
        if (fullName.isBlank() || fullName.length() > 150 || fullName.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("ФИО должно содержать от 1 до 150 символов без управляющих знаков");
        }
        phones = List.copyOf(Objects.requireNonNull(phones, "Добавьте телефон"));
        if (phones.isEmpty() || phones.size() > 20) {
            throw new IllegalArgumentException("У абонента должно быть от 1 до 20 телефонов");
        }
        long unique = phones.stream().map(p -> PhoneNumber.digits(p.number())).distinct().count();
        if (unique != phones.size()) {
            throw new IllegalArgumentException("Один номер не должен повторяться у абонента");
        }
    }

    /**
     * Создаёт абонента с новым случайным UUID.
     */
    public static Contact create(String name, List<PhoneNumber> phones) {
        return new Contact(UUID.randomUUID(), name, phones);
    }

    /**
     * Соединяет телефоны и их типы в строку для таблицы.
     */
    public String phoneSummary() {
        return phones.stream().map(PhoneNumber::toString).collect(java.util.stream.Collectors.joining("; "));
    }
}
