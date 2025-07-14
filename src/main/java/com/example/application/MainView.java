package com.example.application;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.StreamResource;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.web.util.UriComponentsBuilder;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Route("")
@PageTitle("Генератор платежа")
public class MainView extends VerticalLayout {

    private final TextField firstName = new TextField("Имя");
    private final TextField lastName = new TextField("Фамилия");
    private final TextField middleName = new TextField("Отчество");
    private final DatePicker birthDate = new DatePicker("Дата платежа");
    private final NumberField amount = new NumberField("Сумма платежа");
    private final Image qrCodeImage;
    private final Anchor testLink; // Для отображения ссылки QR-кода

    public MainView() {
        setSizeFull();
        setJustifyContentMode(JustifyContentMode.CENTER);
        setAlignItems(Alignment.CENTER);

        Div contentDiv = new Div();
        contentDiv.setClassName("form-container");

        add(new H1("Форма для генерации платежа"));

        FormLayout formLayout = new FormLayout();
        formLayout.add(firstName, lastName, middleName, birthDate, amount);
        formLayout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        Button generateButton = new Button("Сгенерировать QR код", e -> {
            try {
                generateQRCode();
            } catch (WriterException | IOException ex) {
                throw new RuntimeException(ex);
            }
        });
        generateButton.setClassName("primary-button");

        qrCodeImage = new Image();
        qrCodeImage.setVisible(false);

        testLink = new Anchor("", "Проверить QR-код");
        testLink.setTarget("_blank");
        testLink.setVisible(false);

        contentDiv.add(formLayout, generateButton, qrCodeImage, testLink);
        add(contentDiv);
    }

    private void generateQRCode() throws WriterException, IOException {
        String data = buildUrlWithParams();
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, 300, 300); // Увеличим размер для лучшего качества

        // Генерируем PNG-изображение QR-кода
        ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
        BufferedImage originalQrImage = ImageIO.read(new ByteArrayInputStream(pngOutputStream.toByteArray()));

        // Обработка изображения — создаём новую картинку с прозрачностью и помещаем QR-код внутрь неё
        int size = Math.min(originalQrImage.getWidth(), originalQrImage.getHeight()); // Получаем минимальный размер
        BufferedImage roundedQrImage = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = roundedQrImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Заливка фона светло-голубым цветом
        g2d.setColor(new Color(0xEAF3FF)); // Нежно-голубой цвет
        g2d.fillOval(0, 0, size, size); // Заполняем круговым градиентом
        g2d.dispose();


        // Копируем исходный QR-код в новое изображение с закруглёнными углами
        for(int x=0;x<size;x++) {
            for(int y=0;y<size;y++) {
                Color c = new Color(originalQrImage.getRGB(x,y), true);
                if(c.getAlpha()==0 || c.equals(Color.WHITE)) continue; // Пропускаем прозрачные и белые области
                roundedQrImage.setRGB(x, y, c.getRGB());
            }
        }

        // Теперь обработанное изображение с округлыми краями сохраняем в поток
        ByteArrayOutputStream processedPngOutputStream = new ByteArrayOutputStream();
        ImageIO.write(roundedQrImage, "png", processedPngOutputStream);

        // Загружаем логотип и накладываем его в центр
        InputStream logoInputStream = this.getClass().getResourceAsStream("/logo.jpg");
        assert logoInputStream != null;
        BufferedImage logoImage = ImageIO.read(logoInputStream);
        int logoSize = size / 4; // Размер логотипа относительно QR-кода
        logoImage = resizeImage(logoImage, logoSize, logoSize); // Масштабируем логотип
        int posX = (size - logoSize) / 2;
        int posY = (size - logoSize) / 2;

        // Объединение логотипа и QR-кода
        Graphics2D graphics = roundedQrImage.createGraphics();
        graphics.drawImage(logoImage, posX, posY, null);
        graphics.dispose();

        // Преобразование финального изображения обратно в байтовый массив
        ByteArrayOutputStream finalOutputStream = new ByteArrayOutputStream();
        ImageIO.write(roundedQrImage, "png", finalOutputStream);
        byte[] finalPngData = finalOutputStream.toByteArray();

        // Готовое изображение используем дальше
        StreamResource resource = new StreamResource("qr.png", () -> new ByteArrayInputStream(finalPngData));
        qrCodeImage.setSrc(resource);
        qrCodeImage.setVisible(true);

        // Ссылка для проверки QR-кода вручную
        testLink.setHref(data);
        testLink.setVisible(true);
    }

    // Метод масштабирования изображения
    private BufferedImage resizeImage(BufferedImage image, int targetWidth, int targetHeight) {
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TRANSLUCENT);
        Graphics2D graphic = resizedImage.createGraphics();
        graphic.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphic.drawImage(image, 0, 0, targetWidth, targetHeight, null);
        graphic.dispose();
        return resizedImage;
    }

    private String buildUrlWithParams() {
        return UriComponentsBuilder.fromUriString("/pay")
                .queryParam("firstName", firstName.getValue())
                .queryParam("lastName", lastName.getValue())
                .queryParam("middleName", middleName.getValue())
                .queryParam("birthDate", birthDate.getValue() != null ?
                        birthDate.getValue().format(DateTimeFormatter.ISO_DATE) : null)
                .queryParam("amount", amount.getValue())
                .build()
                .encode()
                .toUriString();
    }

    @Route(value = "pay")
    @PageTitle("Оплата")
    public static class PaymentView extends VerticalLayout implements BeforeEnterObserver {

        private final TextField firstNameField = new TextField("Имя");
        private final TextField lastNameField = new TextField("Фамилия");
        private final TextField middleNameField = new TextField("Отчество");
        private final TextField birthDateField = new TextField("Дата платежа");
        private final TextField amountField = new TextField("Сумма платежа");

        public PaymentView() {
            setSizeFull();
            setJustifyContentMode(JustifyContentMode.CENTER);
            setAlignItems(Alignment.CENTER);

            Div contentDiv = new Div();
            contentDiv.setClassName("payment-form-container"); // Изменим класс для страницы оплаты

            add(new H1("Оплата платежа"));

            // Создаем вертикальную компоновку для полей
            VerticalLayout fieldsLayout = new VerticalLayout();
            fieldsLayout.setPadding(false);
            fieldsLayout.setSpacing(false);
            fieldsLayout.setWidthFull();

            // Настраиваем поля
            configureField(firstNameField);
            configureField(lastNameField);
            configureField(middleNameField);
            configureField(birthDateField);
            configureField(amountField);

            // Добавляем поля в вертикальный layout
            fieldsLayout.add(
                    firstNameField,
                    lastNameField,
                    middleNameField,
                    birthDateField,
                    amountField
            );

            Button payButton = new Button("Оплатить", e -> Notification.show("Ваш платёж успешно зарегистрирован"));
            payButton.setClassName("primary-button");
            payButton.setWidthFull();

            contentDiv.add(fieldsLayout, payButton);
            add(contentDiv);
            add(new RouterLink("Вернуться к генератору", MainView.class));
        }

        private void configureField(TextField field) {
            field.setReadOnly(true);
            field.setWidthFull();
            field.getStyle().set("margin-bottom", "16px");
        }

        @Override
        public void beforeEnter(BeforeEnterEvent event) {
            Location location = event.getLocation();
            QueryParameters queryParameters = location.getQueryParameters();
            Map<String, List<String>> parameters = queryParameters.getParameters();

            if (!parameters.isEmpty()) {
                firstNameField.setValue(parameters.getOrDefault("firstName", List.of("")).get(0));
                lastNameField.setValue(parameters.getOrDefault("lastName", List.of("")).get(0));
                middleNameField.setValue(parameters.getOrDefault("middleName", List.of("")).get(0));
                birthDateField.setValue(parameters.getOrDefault("birthDate", List.of("")).get(0));
                amountField.setValue(parameters.getOrDefault("amount", List.of("")).get(0));
            }
        }
    }
    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        getElement().getStyle()
                .set("background-color", "#F4F6FA")
                .set("font-family", "'Inter', sans-serif")
                .set("color", "#3B3A3E")
                .set("display", "flex")
                .set("justify-content", "center")
                .set("align-items", "center")
                .set("min-height", "100vh");

        getUI().ifPresent(ui -> ui.getPage().addStyleSheet("""
        .form-container, .payment-form-container {
          max-width: 500px;
          margin-top: 50px;
          padding: 2rem;
          background-color: #ffffff;
          border-radius: 10px;
          box-shadow: 0 4px 8px rgba(0,0,0,.1);
          width: 100%;
        }
        
        .payment-form-container {
          display: flex;
          flex-direction: column;
          gap: 16px;
        }
        
        vaadin-text-field {
          width: 100%;
        }
        
        vaadin-text-field::part(input-field) {
          background-color: #EFF0F2 !important;
          color: black !important;
          font-size: 16px;
          height: 48px;
          width: 100%;
          border-radius: 8px;
          outline: none;
          border: solid 1px #dfe2eb;
        }
        
        vaadin-text-field::part(label) {
          font-size: 14px;
          color: #3b3a3e;
          text-transform: uppercase;
          letter-spacing: 0.5px;
          opacity: 0.8;
        }
        
        vaadin-date-picker::part(date-input-field) {
          background-color: #EFF0F2 !important;
          color: black !important;
          font-size: 16px;
          height: 48px;
          width: 100%;
          border-radius: 8px;
          outline: none;
          border: solid 1px #dfe2eb;
        }
        
        .primary-button {
          display: block;
          width: 100%;
          padding: 12px 24px;
          font-size: 16px;
          font-weight: bold;
          color: white;
          background-color: #4F7DF7;
          border-radius: 8px;
          border: none;
          cursor: pointer;
          transition: background-color 0.3s ease;
          margin-top: 16px;
        }
        
        .primary-button:hover {
          background-color: #3B59D1;
        }
        """
        ));
    }
}