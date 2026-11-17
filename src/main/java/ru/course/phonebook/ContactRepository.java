package ru.course.phonebook;

import java.io.IOException;
import java.util.List;

public interface ContactRepository {
    List<Contact> load() throws IOException;

    void save(List<Contact> contacts) throws IOException;
}
