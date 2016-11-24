package com.company.sample.web.screens;

import com.haulmont.cuba.core.app.FileStorageService;
import com.haulmont.cuba.core.entity.FileDescriptor;
import com.haulmont.cuba.core.global.FileStorageException;
import com.haulmont.cuba.gui.components.AbstractWindow;
import com.haulmont.cuba.gui.components.Embedded;
import com.haulmont.cuba.gui.components.FileUploadField;
import com.haulmont.cuba.gui.data.DataSupplier;
import com.haulmont.cuba.gui.export.ExportDisplay;
import com.haulmont.cuba.gui.export.ExportFormat;
import com.haulmont.cuba.gui.upload.FileUploadingAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

public class Screen extends AbstractWindow {
    private static final int IMG_HEIGHT = 215;
    private static final int IMG_WIDTH = 250;

    private Logger log = LoggerFactory.getLogger(Screen.class);

    @Inject
    private DataSupplier dataSupplier;
    @Inject
    private FileStorageService fileStorageService;
    @Inject
    private FileUploadingAPI fileUploadingAPI;
    @Inject
    private ExportDisplay exportDisplay;
    @Inject
    private Embedded embeddedImage;
    @Inject
    private FileUploadField uploadField;

    private FileDescriptor imageDescriptor;

    @Override
    public void init(Map<String, Object> params) {
        uploadField.addFileUploadSucceedListener(event -> {
            FileDescriptor fd = uploadField.getFileDescriptor();
            File imageFile = fileUploadingAPI.getFile(uploadField.getFileId());
            if (imageFile != null) {
                BufferedImage image;
                try {
                    byte[] bytes = Files.readAllBytes(imageFile.toPath());
                    image = ImageIO.read(new ByteArrayInputStream(bytes));
                    int width = image.getWidth();
                    int height = image.getHeight();

                    if (((double) height / (double) width) > ((double) IMG_HEIGHT / (double) IMG_WIDTH)) {
                        image = resize(image, width * IMG_HEIGHT / height, IMG_HEIGHT);
                    } else {
                        image = resize(image, IMG_WIDTH, height * IMG_WIDTH / width);
                    }

                    ImageIO.write(image, fd.getExtension(), imageFile);
                } catch (IOException e) {
                    log.error("Unable to resize image", e);
                }
            }

            try {
                fileUploadingAPI.putFileIntoStorage(uploadField.getFileId(), fd);
            } catch (FileStorageException e) {
                throw new RuntimeException("Error saving file to FileStorage", e);
            }
            imageDescriptor = dataSupplier.commit(fd);
            displayImage();
        });

        uploadField.addFileUploadErrorListener(event ->
                showNotification("File upload error", NotificationType.HUMANIZED));
    }

    private BufferedImage resize(BufferedImage origin, int newWidth, int newHeight) {
        Image scaledInstance = origin.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
        BufferedImage image = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2d = image.createGraphics();
        g2d.drawImage(scaledInstance, 0, 0, null);
        g2d.dispose();

        return image;
    }

    public void onDownloadImageBtnClick() {
        if (imageDescriptor != null)
            exportDisplay.show(imageDescriptor, ExportFormat.OCTET_STREAM);
    }

    public void onClearImageBtnClick() {
        imageDescriptor = null;
        displayImage();
    }

    private void displayImage() {
        byte[] bytes = null;
        if (imageDescriptor != null) {
            try {
                bytes = fileStorageService.loadFile(imageDescriptor);
            } catch (FileStorageException e) {
                log.error("Unable to load image file", e);
                showNotification("Unable to load image file", NotificationType.HUMANIZED);
            }
        }
        if (bytes != null) {
            embeddedImage.setSource(imageDescriptor.getName(), new ByteArrayInputStream(bytes));
            embeddedImage.setType(Embedded.Type.IMAGE);
            BufferedImage image;
            try {
                image = ImageIO.read(new ByteArrayInputStream(bytes));
                int width = image.getWidth();
                int height = image.getHeight();

                embeddedImage.setWidth(String.valueOf(width));
                embeddedImage.setHeight(String.valueOf(height));
            } catch (IOException e) {
                log.error("Unable to resize image", e);
            }
            // refresh image
            embeddedImage.setVisible(false);
            embeddedImage.setVisible(true);
        } else {
            embeddedImage.setVisible(false);
        }
    }
}