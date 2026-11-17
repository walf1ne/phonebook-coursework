package ru.course.phonebook;

import java.io.IOException;
import java.util.List;

/**
 * Определяет операции чтения и сохранения базы абонентов.
 */
public interface ContactRepository {
    /**
     * Читает базу; отсутствующий файл означает пустой справочник, повреждение вызывает IOException.
     */
    List<Contact> load() throws IOException;

    /**
     * Записывает проверенные данные во временный файл, сохраняет предыдущую версию и атомарно заменяет базу.
     */
    void save(List<Contact> contacts) throws IOException;
}
