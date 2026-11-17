package ru.course.phonebook;

import javafx.application.Application;

/**
 * Запускает JavaFX из JAR через обычный главный класс.
 */
public final class Launcher {
    /**
     * Запрещает создание экземпляров класса запуска.
     */
    private Launcher() {
    }

    /**
     * Передаёт аргументы командной строки механизму запуска JavaFX.
     */
    public static void main(String[] args) {
        Application.launch(PhoneBookApp.class, args);
    }
}
