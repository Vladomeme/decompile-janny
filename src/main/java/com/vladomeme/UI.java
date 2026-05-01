package com.vladomeme;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class UI extends JFrame {

    List<ExecutionOption> options;
    boolean saveToCopy = true;

    public UI() {
        setSize(1000, 1000);
        setTitle("Janny");
        setLayout(new BorderLayout());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setIconImage(new ImageIcon(Objects.requireNonNull(getClass().getResource("/icon.png"))).getImage());

        Box topBox = new Box(BoxLayout.Y_AXIS);
        topBox.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.BLACK));

        JLabel topLabel = new JLabel();
        topLabel.setIcon(new ImageIcon(new ImageIcon(Objects.requireNonNull(getClass().getResource("/top_logo.png")))
                .getImage().getScaledInstance(600, 100, Image.SCALE_SMOOTH)));
        topLabel.setPreferredSize(new Dimension(1000, 100));
        topLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        topBox.add(topLabel);

        JPanel topPanel = new JPanel(new BorderLayout(20, 20));

        Box fileSelectionBox = new Box(BoxLayout.X_AXIS);
        fileSelectionBox.setBorder(BorderFactory.createEmptyBorder(15, 5, 3, 3));

        JTextField selectionField = new JTextField(50);
        selectionField.setFont(new Font("Arial", Font.BOLD, 16));
        selectionField.setBorder(BorderFactory.createEmptyBorder(15, 5, 3, 3));
        selectionField.setEditable(false);
        selectionField.setMaximumSize(new Dimension(480, 30));
        JButton selectionButton = new JButton("Select file");
        selectionButton.setFont(new Font("Arial", Font.BOLD, 14));
        selectionButton.addActionListener(e -> {
            String file = selectFile();
            SwingUtilities.invokeLater(() -> selectionField.setText(file));
        });
        fileSelectionBox.add(selectionButton);
        fileSelectionBox.add(selectionField);

        topPanel.add(fileSelectionBox, BorderLayout.WEST);

        Box fileCopyBox = new Box(BoxLayout.X_AXIS);
        fileCopyBox.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 5));
        JLabel fileCopyLabel = new JLabel();
        fileCopyLabel.setText("Output to a new file");
        fileCopyLabel.setFont(new Font("Arial", Font.BOLD, 16));
        fileCopyLabel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 5));
        JCheckBox fileCopyCheckBox = new JCheckBox();
        fileCopyCheckBox.setSelected(true);
        fileCopyCheckBox.addActionListener(e -> saveToCopy = fileCopyCheckBox.isSelected());
        fileCopyBox.add(fileCopyCheckBox);
        fileCopyBox.add(fileCopyLabel);
        topPanel.add(fileCopyBox, BorderLayout.EAST);

        topBox.add(topPanel);
        add(topBox, BorderLayout.NORTH);

        Box centerBox = new Box(BoxLayout.X_AXIS);

        Box optionListPanel = new Box(BoxLayout.Y_AXIS);
        optionListPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, Color.BLACK));
        optionListPanel.setAlignmentY(Component.TOP_ALIGNMENT);
        optionListPanel.setMinimumSize(new Dimension(400, 5000));
        optionListPanel.setMaximumSize(new Dimension(400, 5000));

        JLabel optionsHeader = new JLabel("Option selection");
        optionsHeader.setMinimumSize(new Dimension(400, 20));
        optionsHeader.setMaximumSize(new Dimension(400, 20));
        optionsHeader.setFont(new Font("Arial", Font.BOLD, 16));
        optionsHeader.setBorder(BorderFactory.createEmptyBorder(1, 1, 5, 1));
        optionListPanel.add(optionsHeader);

        centerBox.add(optionListPanel);

        Box descPanel = new Box(BoxLayout.Y_AXIS);
        descPanel.setBorder(BorderFactory.createMatteBorder(0, 1, 1, 0, Color.BLACK));
        descPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        descPanel.setMinimumSize(new Dimension(600, 20));

        JPanel headerPanel = new JPanel();
        JLabel descHeader = new JLabel("Option description");
        descHeader.setHorizontalAlignment(SwingConstants.LEFT);
        descHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        descHeader.setMinimumSize(new Dimension(600, 20));
        descHeader.setMaximumSize(new Dimension(600, 20));
        descHeader.setFont(new Font("Arial", Font.BOLD, 16));
        descHeader.setBorder(BorderFactory.createEmptyBorder(1, 1, 10, 1));
        headerPanel.add(descHeader);
        descPanel.add(headerPanel);

        JTextArea descArea = new JTextArea();
        descArea.setEditable(false);
        descArea.setCursor(null);
        descArea.setOpaque(false);
        descArea.setFocusable(false);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        descArea.setFont(new Font("Arial", Font.BOLD, 14));
        descArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        descArea.setText("Click on (?) next to options to see their description here!");
        descHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        descPanel.add(descArea);

        centerBox.add(descPanel);

        add(centerBox, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JButton executeButton = new JButton("Execute");
        executeButton.setFont(new Font("Arial", Font.BOLD, 14));
        executeButton.addActionListener(e -> Executor.execute(options, Path.of(selectionField.getText()), saveToCopy));
        bottomPanel.add(executeButton, BorderLayout.WEST);

        add(bottomPanel, BorderLayout.SOUTH);

        options = new ArrayList<>();

        options.add(new ExecutionOption(optionListPanel, descHeader, descArea, null, Executor::removeUselessDecompilerComments,
                "Remove useless decompiler comments", OptionDescriptions.removeUselessDecompilerComments));

        options.add(new ExecutionOption(optionListPanel, descHeader, descArea, null, Executor::removeNullPointerTypeCasts,
                "Remove null pointer type casts", OptionDescriptions.removeNullPointerTypeCasts));

        options.add(new ExecutionOption(optionListPanel, descHeader, descArea, null, Executor::removeMethodInitialization,
                "Remove method initialization blocks", OptionDescriptions.removeMethodInitialization));

        options.add(new ExecutionOption(optionListPanel, descHeader, descArea, null, Executor::removeStaticInitialization,
                "Remove static initialization blocks", OptionDescriptions.removeStaticInitialization));

        options.add(new ExecutionOption(optionListPanel, descHeader, descArea, Executor::simplifyProcedureInterruptions$prepare, Executor::simplifyProcedureInterruptions,
                "Simplify procedure interruptions", OptionDescriptions.simplifyProcedureInterruptions));

        options.add(new ExecutionOption(optionListPanel, descHeader, descArea, null, Executor::simplifyObjectReferences,
                "Simplify object references", OptionDescriptions.simplifyObjectReferences));

        options.add(new ExecutionOption(optionListPanel, descHeader, descArea, null, Executor::replaceNullChecks,
                "Replace null checks", OptionDescriptions.replaceNullChecks));

        options.add(new ExecutionOption(optionListPanel, descHeader, descArea, null, Executor::removeArrayBoundChecks,
                "Remove array bound checks", OptionDescriptions.removeArrayBoundChecks));

        options.add(new ExecutionOption(optionListPanel, descHeader, descArea, Executor::simplifyArrayAccess$prepare, Executor::simplifyArrayAccess,
                "Simplify array access", OptionDescriptions.simplifyArrayAccess));

        options.add(new ExecutionOption(optionListPanel, descHeader, descArea, null, Executor::formatGenericTypes,
                "Format generic types", OptionDescriptions.formatGenericTypes));

        options.add(new ExecutionOption(optionListPanel, descHeader, descArea, Executor::removeMethodInfoArgument$prepare, Executor::removeMethodInfoArgument,
                "Remove useless MethodInfo arguments", OptionDescriptions.removeMethodInfoArguments));

        options.add(new ExecutionOption(optionListPanel, descHeader, descArea, null, Executor::replaceUnderscoresForMethods,
                "Replace underscores for methods", OptionDescriptions.replaceUnderscoresForMethods));

        setVisible(true);
    }

    String selectFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileFilter() {
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().endsWith(".c");
            }

            public String getDescription() {
                return "Decompiled code (.c)";
            }
        });
        fileChooser.setCurrentDirectory(Path.of("").toAbsolutePath().toFile());

        int response = fileChooser.showOpenDialog(null);

        if (response == JFileChooser.APPROVE_OPTION) {
            return fileChooser.getSelectedFile().getAbsolutePath();
        }
        return "";
    }
}
