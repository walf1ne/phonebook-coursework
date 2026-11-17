package ru.course.phonebook;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.embed.swing.SwingFXUtils;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;

public class GuiCheck extends Application {
    private PhoneBookApp app;
    private Stage stage;
    private Path screenshots;
    private int checks;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        screenshots = Path.of("build/gui-check");
        Files.createDirectories(screenshots);
        Path db = Path.of("build/gui-check/contacts.pbk");
        Files.deleteIfExists(db);
        try (var repository = new BinaryContactRepository(db)) {
            List<Contact> contacts = new ArrayList<>();
            contacts.add(Contact.create("Иванов Иван Иванович", List.of(
                    new PhoneNumber("+7 (900) 000-00-01", PhoneType.MOBILE),
                    new PhoneNumber("+7 (812) 000-00-01", PhoneType.WORK))));
            contacts.add(Contact.create("Петров Пётр Петрович", List.of(new PhoneNumber("+7 (900) 000-00-02", PhoneType.HOME))));
            contacts.add(Contact.create("Сергеев Сергей Сергеевич", List.of(new PhoneNumber("+7 (812) 000-00-03", PhoneType.FAX))));
            for (int i = 0; i < 27; i++) {
                contacts.add(Contact.create(String.format("Тестовый абонент %02d", i + 1), List.of(
                        new PhoneNumber(String.format("+7 (900) 000-00-%02d", i + 4), PhoneType.MOBILE))));
            }
            repository.save(contacts);
        }
        app = new PhoneBookApp();
        stage = primaryStage;
        app.open(stage, db);
        later(this::mainWindow);
    }

    private void later(Runnable action) {
        PauseTransition pause = new PauseTransition(Duration.millis(500));
        pause.setOnFinished(event -> Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable exception) {
                exception.printStackTrace();
                app.stop();
                Platform.exit();
                System.exit(1);
            }
        }));
        pause.play();
    }

    private void require(boolean condition) {
        if (!condition) {
            throw new AssertionError("GUI check " + checks);
        }
        checks++;
    }

    private void snapshot(Scene scene, String name) {
        try {
            scene.getRoot().applyCss();
            scene.getRoot().layout();
            ImageIO.write(SwingFXUtils.fromFXImage(scene.snapshot(null), null), "png", screenshots.resolve(name + ".png").toFile());
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private TableView<?> table() {
        return (TableView<?>) stage.getScene().lookup(".table-view");
    }

    private void mainWindow() {
        require(table().getItems().size() == 30);
        snapshot(stage.getScene(), "01-main");
        ((TextField) stage.getScene().lookup("#search")).setText("9000000002");
        later(this::searchResult);
    }

    private void searchResult() {
        require(table().getItems().size() == 1);
        snapshot(stage.getScene(), "02-search");
        ((TextField) stage.getScene().lookup("#search")).setText("нет такого абонента");
        later(this::emptyResult);
    }

    private void emptyResult() {
        require(table().getItems().isEmpty());
        snapshot(stage.getScene(), "03-empty");
        ((TextField) stage.getScene().lookup("#search")).clear();
        later(this::openEditor);
    }

    private void openEditor() {
        later(this::invalidInput);
        ((Button) stage.getScene().lookup("#add")).fire();
        later(this::afterSave);
    }

    private DialogPane dialog() {
        return (DialogPane) Window.getWindows().stream().filter(window -> window.isShowing() && window != stage)
                .findFirst().orElseThrow().getScene().getRoot();
    }

    private void invalidInput() {
        DialogPane pane = dialog();
        ((TextField) pane.lookup("#phone-number")).setText("12345");
        ((Button) pane.lookupButton(ButtonType.OK)).fire();
        require(pane.getScene().getWindow().isShowing());
        snapshot(pane.getScene(), "07-empty-name");
        ((TextField) pane.lookup("#contact-name")).setText("Фёдоров Фёдор Фёдорович");
        ((TextField) pane.lookup("#phone-number")).setText("abc");
        ((Button) pane.lookupButton(ButtonType.OK)).fire();
        require(pane.getScene().getWindow().isShowing());
        snapshot(pane.getScene(), "04-invalid");
        ((TextField) pane.lookup("#phone-number")).setText("+7 (900) 000-00-99");
        ((Button) pane.lookupButton(ButtonType.OK)).fire();
    }

    private void afterSave() {
        require(table().getItems().size() == 31);
        snapshot(stage.getScene(), "05-saved");
        ((Slider) stage.getScene().lookup(".slider")).setValue(140);
        later(this::zoomed);
    }

    private void zoomed() {
        snapshot(stage.getScene(), "06-zoom");
        require(stage.getScene().getRoot().getStyle().contains("19.6"));
        app.stop();
        try {
            Path demo = Path.of("examples/demo.pbk");
            Files.createDirectories(demo.getParent());
            Files.copy(Path.of("build/gui-check/contacts.pbk"), demo, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(screenshots.resolve("result.txt"), "GUI checks passed: " + checks + "\n");
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
        stage.close();
        Platform.exit();
    }
}
