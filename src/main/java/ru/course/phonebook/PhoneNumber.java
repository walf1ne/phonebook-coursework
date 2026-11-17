package ru.course.phonebook;

import java.util.Objects;

public record PhoneNumber(String number, PhoneType type) {
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

    public static String digits(String value) {
        return value.replaceAll("[^0-9]", "");
    }

    @Override
    public String toString() {
        return type + ": " + number;
    }
}
