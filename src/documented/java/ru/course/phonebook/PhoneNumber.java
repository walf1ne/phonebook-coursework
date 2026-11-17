package ru.course.phonebook;

import java.util.Objects;

/**
 * Хранит и проверяет номер телефона вместе с его типом.
 */
public record PhoneNumber(String number, PhoneType type) {
    /**
     * Проверяет допустимые символы и количество цифр номера.
     */
    public PhoneNumber {
        Objects.requireNonNull(number, "Введите номер телефона");
        Objects.requireNonNull(type, "Выберите тип телефона");
        number = number.strip();
        if (!number.matches("\\+?[0-9 ()-]{5,32}")) {
            throw new IllegalArgumentException("Номер: цифры, пробелы, скобки, дефис и + в начале");
        }
        int length = digits(number).length();
        if (length < 5 || length > 15) {
            throw new IllegalArgumentException("Номер должен содержать от 5 до 15 цифр");
        }
    }

    /**
     * Оставляет только цифры ASCII для сравнения номеров независимо от оформления.
     */
    public static String digits(String value) {
        return value.replaceAll("[^0-9]", "");
    }

    /**
     * Возвращает тип и номер для отображения в таблице.
     */
    @Override
    public String toString() {
        return type + ": " + number;
    }
}
