package ru.course.phonebook;

import java.io.IOException;
import java.nio.file.Path;
import java.text.Collator;
import java.util.Locale;
import javafx.application.Application;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Управляет главным окном телефонного справочника.
 */
public final class PhoneBookApp extends Application {
    private static final Logger LOGGER = LogManager.getLogger(PhoneBookApp.class);
    private final TableView<Contact> table = new TableView<>();
    private final TextField search = new TextField();
    private final Label status = new Label();
    private final CheckBox descending = new CheckBox("Я → А");
    private BinaryContactRepository repository;
    private ContactService service;
    private Stage stage;
    private VBox root;

    /**
     * Получает путь базы из параметра db и открывает главное окно.
     */
    @Override
    public void start(Stage primaryStage) {
        open(primaryStage, Path.of(getParameters().getNamed().getOrDefault("db", "data/contacts.pbk")));
    }

    /**
     * Открывает указанную базу; при ошибке показывает сообщение, сохраняя исходный файл.
     */
    public void open(Stage primaryStage, Path path) {
        stage = primaryStage;
        try {
            repository = new BinaryContactRepository(path);
            service = new ContactService(repository);
            buildWindow(path);
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Не удалось открыть базу: {}", exception.getClass().getSimpleName());
            showError("База не открыта. Исходный файл не изменён.\n" + exception.getMessage());
            javafx.application.Platform.exit();
        }
    }

    /**
     * Собирает главное окно, задаёт минимальные размеры и выводит текущие записи.
     */
    private void buildWindow(Path path) {
        Label title = new Label("Телефонный справочник");
        title.setStyle("-fx-font-size: 25px; -fx-font-weight: bold; -fx-text-fill: #16324f;");
        Label subtitle = new Label("Абоненты и телефоны • локальная база данных");
        subtitle.setStyle("-fx-text-fill: #526477;");
        configureTable();
        root = new VBox(14, title, subtitle, buildSearchBar(), buildActions(), table, status);
        root.setPadding(new Insets(22));
        root.setStyle("-fx-font-family: 'Arial'; -fx-font-size: 14px; -fx-background-color: #f5f7fa;");
        VBox.setVgrow(table, Priority.ALWAYS);
        stage.setScene(new Scene(root, 1100, 680));
        stage.setMinWidth(780);
        stage.setMinHeight(480);
        stage.setTitle("Телефонный справочник — " + path.getFileName());
        refresh();
        stage.show();
    }

    /**
     * Создаёт строку живого поиска, сброс и переключатель направления сортировки.
     */
    private HBox buildSearchBar() {
        search.setPromptText("Поиск по ФИО или номеру телефона");
        search.setId("search");
        search.textProperty().addListener((observable, oldValue, newValue) -> refresh());
        Button clear = new Button("Сбросить");
        clear.setOnAction(event -> search.clear());
        descending.setOnAction(event -> {
            table.getSortOrder().clear();
            refresh();
        });
        HBox bar = new HBox(10, search, clear, descending);
        HBox.setHgrow(search, Priority.ALWAYS);
        return bar;
    }

    /**
     * Создаёт команды добавления, изменения, удаления и масштабирования текста.
     */
    private HBox buildActions() {
        Button add = new Button("+ Добавить");
        add.setId("add");
        add.setOnAction(event -> edit(null));
        Button edit = new Button("Изменить");
        edit.setOnAction(event -> edit(table.getSelectionModel().getSelectedItem()));
        Button delete = new Button("Удалить");
        delete.setOnAction(event -> deleteSelected());
        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        delete.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        Slider zoom = new Slider(80, 160, 100);
        zoom.setPrefWidth(150);
        Label percentage = new Label("100 %");
        zoom.valueProperty().addListener((observable, oldValue, value) -> {
            root.setStyle("-fx-font-family: 'Arial'; -fx-font-size: " + 14 * value.doubleValue() / 100
                    + "px; -fx-background-color: #f5f7fa;");
            percentage.setText(Math.round(value.doubleValue()) + " %");
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return new HBox(10, add, edit, delete, spacer, new Label("Масштаб"), zoom, percentage);
    }

    /**
     * Настраивает столбцы, русскую сортировку ФИО и открытие редактора двойным щелчком.
     */
    private void configureTable() {
        TableColumn<Contact, String> names = new TableColumn<>("ФИО абонента");
        names.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().fullName()));
        names.setComparator(Collator.getInstance(Locale.forLanguageTag("ru"))::compare);
        names.setPrefWidth(360);
        TableColumn<Contact, String> phones = new TableColumn<>("Номера телефонов и типы");
        phones.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().phoneSummary()));
        phones.setPrefWidth(640);
        table.getColumns().add(names);
        table.getColumns().add(phones);
        table.setPlaceholder(new Label("Абоненты не найдены. Измените поиск или добавьте запись."));
        table.setRowFactory(view -> {
            TableRow<Contact> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    edit(row.getItem());
                }
            });
            return row;
        });
    }

    /**
     * Заново применяет фильтр и сортировку и обновляет счётчик видимых записей.
     */
    private void refresh() {
        table.getItems().setAll(service.search(search.getText(), descending.isSelected()));
        table.sort();
        status.setText("Показано: " + table.getItems().size() + " из " + service.size()
                + "   •   Изменения сохраняются автоматически");
    }

    /**
     * Открывает редактор и сохраняет подтверждённые изменения через сервис.
     */
    private void edit(Contact contact) {
        new ContactEditor(stage, contact).show().ifPresent(updated -> {
            try {
                service.upsert(updated);
                refresh();
            } catch (IOException | RuntimeException exception) {
                operationFailed(exception);
            }
        });
    }

    /**
     * Запрашивает подтверждение и удаляет выбранную запись; отмена не меняет базу.
     */
    private void deleteSelected() {
        Contact contact = table.getSelectionModel().getSelectedItem();
        if (contact == null) {
            return;
        }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION, "Удалить выбранного абонента?", ButtonType.YES, ButtonType.NO);
        confirmation.initOwner(stage);
        confirmation.setHeaderText("Подтверждение удаления");
        if (confirmation.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
            try {
                service.delete(contact.id());
                refresh();
            } catch (IOException | RuntimeException exception) {
                operationFailed(exception);
            }
        }
    }

    /**
     * Регистрирует тип ошибки и сообщает пользователю, что изменения не сохранены.
     */
    private void operationFailed(Exception exception) {
        LOGGER.error("Операция отклонена: {}", exception.getClass().getSimpleName());
        showError("Изменения не сохранены.\n" + exception.getMessage());
    }

    /**
     * Показывает модальное окно с описанием ошибки.
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setHeaderText("Не удалось выполнить операцию");
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Закрывает хранилище и освобождает блокировку при завершении приложения.
     */
    @Override
    public void stop() {
        if (repository != null) {
            try {
                repository.close();
            } catch (IOException exception) {
                LOGGER.warn("Не удалось освободить блокировку базы");
            }
        }
    }
}
