package ru.course.phonebook;

/**
 * Обозначает назначение телефонного номера.
 */
public enum PhoneType {
    MOBILE("Сотовый"), HOME("Домашний"), WORK("Рабочий"), FAX("Факс"), OTHER("Другой");

    private final String label;

    /**
     * Создаёт тип с русским наименованием для интерфейса.
     */
    PhoneType(String label) {
        this.label = label;
    }

    /**
     * Возвращает русское наименование типа телефона.
     */
    @Override
    public String toString() {
        return label;
    }
}
