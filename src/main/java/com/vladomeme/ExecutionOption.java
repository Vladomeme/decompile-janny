package com.vladomeme;

import javax.swing.*;
import java.awt.*;

public class ExecutionOption {
    final Runnable preparation;
    final Runnable execution;
    boolean enabled = true;

    ExecutionOption(Box listBox, JLabel descHeader, JTextArea descArea, Runnable preparation, Runnable execution, String shortDescription, String longDescription) {
        this.preparation = preparation;
        this.execution = execution;

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout(10, 10));
        panel.setMaximumSize(new Dimension(5000, 30));
        panel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        listBox.add(panel);

        Box box = new Box(BoxLayout.X_AXIS);
        panel.add(box, BorderLayout.WEST);

        JCheckBox checkBox = new JCheckBox();
        checkBox.setSelected(true);
        checkBox.setSize(30, 30);
        checkBox.addActionListener(e -> enabled = checkBox.isSelected());
        box.add(checkBox);

        JLabel textLabel = new JLabel(shortDescription);
        textLabel.setSize(400, 10);
        textLabel.setFont(new Font("Arial", Font.BOLD, 16));
        box.add(textLabel);

        JButton button = new JButton();
        button.setText("?");
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setSize(10, 10);
        button.addActionListener(e -> setDescription(descHeader, descArea, shortDescription, longDescription));
        panel.add(button, BorderLayout.EAST);
    }

    void setDescription(JLabel descHeader, JTextArea descArea, String shortDescription, String longDescription) {
        descHeader.setText("-- " + shortDescription + " --");
        descArea.setText(longDescription);
    }
}