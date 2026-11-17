package ru.course.phonebook;

import java.util.*;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

public final class ContactEditor {
    private final Dialog<Contact> dialog = new Dialog<>();
    private final TextField name = new TextField();
    private final VBox phoneRows = new VBox(8);
    private final Label error = new Label();
    private final UUID id;

    public ContactEditor(Window owner, Contact contact) {
        id = contact == null ? UUID.randomUUID() : contact.id();
        dialog.initOwner(owner);
        dialog.setTitle(contact == null ? "Добавление абонента" : "Изменение абонента");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(ButtonType.OK).setAccessibleText("Сохранить абонента");
        name.setPromptText("Фамилия Имя Отчество");
        name.setId("contact-name");
        if (contact != null) {
            name.setText(contact.fullName());
            contact.phones().forEach(this::addPhoneRow);
        } else {
            addPhoneRow(null);
        }
        configureContent();
        configureSave();
    }

    private void configureContent() {
        Button add = new Button("+ Телефон");
        add.setOnAction(event -> {
            if (phoneRows.getChildren().size() < 20) {
                addPhoneRow(null);
            } else {
                error.setText("Максимум 20 телефонов у абонента");
            }
        });
        error.setStyle("-fx-text-fill: #b42318;");
        error.setWrapText(true);
        ScrollPane scroll = new ScrollPane(phoneRows);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(180);
        VBox content = new VBox(12, new Label("ФИО"), name, new Label("Телефоны и их типы"), scroll, add, error);
        content.setPadding(new Insets(16));
        content.setPrefWidth(590);
        dialog.getDialogPane().setContent(content);
    }

    private void addPhoneRow(PhoneNumber phone) {
        TextField number = new TextField(phone == null ? "" : phone.number());
        number.setPromptText("+7 (900) 000-00-00");
        number.setId("phone-number");
        ComboBox<PhoneType> type = new ComboBox<>();
        type.getItems().setAll(PhoneType.values());
        type.setValue(phone == null ? PhoneType.MOBILE : phone.type());
        Button remove = new Button("Удалить");
        HBox row = new HBox(8, number, type, remove);
        HBox.setHgrow(number, Priority.ALWAYS);
        remove.setOnAction(event -> phoneRows.getChildren().remove(row));
        phoneRows.getChildren().add(row);
    }

    private void configureSave() {
        Button save = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        save.setText("Сохранить");
        save.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                readContact();
            } catch (IllegalArgumentException exception) {
                error.setText(exception.getMessage());
                event.consume();
            }
        });
        dialog.setResultConverter(button -> button == ButtonType.OK ? readContact() : null);
    }

    @SuppressWarnings("unchecked")
    private Contact readContact() {
        List<PhoneNumber> phones = phoneRows.getChildren().stream().map(node -> {
            HBox row = (HBox) node;
            String number = ((TextField) row.getChildren().get(0)).getText();
            PhoneType type = ((ComboBox<PhoneType>) row.getChildren().get(1)).getValue();
            return new PhoneNumber(number, type);
        }).toList();
        return new Contact(id, name.getText(), phones);
    }

    public Optional<Contact> show() {
        return dialog.showAndWait();
    }
}
