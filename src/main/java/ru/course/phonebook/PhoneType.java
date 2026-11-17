package ru.course.phonebook;

public enum PhoneType {
    MOBILE("Сотовый"), HOME("Домашний"), WORK("Рабочий"), FAX("Факс"), OTHER("Другой");

    private final String label;

    PhoneType(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
