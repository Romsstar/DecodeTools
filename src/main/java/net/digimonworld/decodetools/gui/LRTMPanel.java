package net.digimonworld.decodetools.gui;

import javax.swing.*;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;

import net.digimonworld.decodetools.gui.util.JHexSpinner;
import net.digimonworld.decodetools.res.payload.LRTMPayload;
import net.digimonworld.decodetools.res.payload.LRTMPayload.LRTMShadingType;
import net.digimonworld.decodetools.res.payload.LRTMPayload.LRTMUnkownType;
import javax.swing.plaf.basic.BasicSliderUI;
import javax.swing.text.NumberFormatter;

import java.awt.*;
import javax.swing.JTextField;

import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.beans.PropertyChangeListener;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParsePosition;


public class LRTMPanel extends PayloadPanel {
    private static final long serialVersionUID = 1L;

    private LRTMPayload selectedLRTM;

    private final JComboBox<LRTMShadingType> shadingSelector = new JComboBox<>();
    private final JComboBox<LRTMUnkownType> unk1Selector = new JComboBox<>();
    private final JSlider ambientSlider = createStyledSlider();
    private final JSlider specularSlider = createStyledSlider();
    private final JSlider emitSlider = createStyledSlider(); // Emit Slider
    private final JLabel shadingLabel = new JLabel("Shading:");
    private final JLabel ambientLabel = new JLabel("Ambient:");
    private final JLabel specularLabel = new JLabel("Diffusion:");
    private final JLabel emitLabel = new JLabel("Emit:");
    private final JLabel unk1Label = new JLabel("Fragment Lightning:");
 
    private final JLabel unk2Label = new JLabel("Specular0:");
    private final JLabel unk3Label = new JLabel("Specular1:");
    private final JSlider spec0Slider =createStyledSlider();
    private final JSlider spec1Slider= createStyledSlider();

    private final JLabel filterLabel = new JLabel("Color Filter:");
    
    private final JButton filterSwatch = new JButton();
    
    private final JTextField ambientValueLabel = new JTextField("0"); // Display normalized ambient value
    private final JTextField specularValueLabel = new JTextField("0"); // Display normalized specular value
    private final JTextField emitValueLabel = new JTextField ("0"); // Display normalized emit value
    private final JTextField spec0ValueLabel = new JTextField("0");
    private final JTextField spec1ValueLabel = new JTextField("0");
    
    public LRTMPanel(Object selected) {
        setSelectedFile(selected);

        shadingLabel.setLabelFor(shadingSelector);
        ambientLabel.setLabelFor(ambientSlider);
        specularLabel.setLabelFor(specularSlider);
        emitLabel.setLabelFor(emitSlider);


        
        // Spec0 Slider Listener
        spec0Slider.addChangeListener(e -> {
            if (selectedLRTM != null) {
                float normalizedValue = spec0Slider.getValue() / 1000.0f;
                int packedColor = denormalizeSliderToRGBA(normalizedValue);
                selectedLRTM.setColor4(packedColor);
                spec0ValueLabel.setText(String.format("%.3f", normalizedValue)); // Update display
            }
        });

        // Spec1 Slider Listener
        spec1Slider.addChangeListener(e -> {
            if (selectedLRTM != null) {
                float normalizedValue = spec1Slider.getValue() / 1000.0f;
                int packedColor = denormalizeSliderToRGBA(normalizedValue);
                selectedLRTM.setColor5(packedColor);
                spec1ValueLabel.setText(String.format("%.3f", normalizedValue)); // Update display
            }
        });
        
        // Ambient Slider Listener
        ambientSlider.addChangeListener(e -> {
            if (selectedLRTM != null) {
                float normalizedValue = ambientSlider.getValue() / 1000.0f; // Slider value (0.0 to 1.0)
                int packedColor = denormalizeSliderToRGBA(normalizedValue); // Convert normalized value to packed RGBA
                selectedLRTM.setColor1(packedColor); // Update payload
                ambientValueLabel.setText(String.format("%.3f", normalizedValue)); // Update display label
            }
        });        



        // Diffusion Slider Listener
        specularSlider.addChangeListener(e -> {
            if (selectedLRTM != null) {
                float normalizedValue = specularSlider.getValue() / 1000.0f; // Slider value (0.0 to 1.0)
                int packedColor = denormalizeSliderToRGBA(normalizedValue); // Convert normalized value to packed RGBA
                selectedLRTM.setColor2(packedColor); // Update payload
                specularValueLabel.setText(String.format("%.3f", normalizedValue)); // Update display label
            }
        });

        // Emit Slider Listener
        emitSlider.addChangeListener(e -> {
            if (selectedLRTM != null) {
                float normalizedValue = emitSlider.getValue() / 1000.0f; // Slider value (0.0 to 1.0)
                int packedRGB = denormalizeSliderToRGB(normalizedValue); // Convert normalized value to packed RGB
                int packedARGB = (0xFF << 24) | packedRGB; // Ensure Alpha remains 255
                selectedLRTM.setColor3(packedARGB); // Update payload
                emitValueLabel.setText(String.format("%.3f", normalizedValue)); // Update display label
            }
        });
        
        addTextFieldListener(ambientValueLabel, ambientSlider);
        addTextFieldListener(specularValueLabel, specularSlider);
        addTextFieldListener(emitValueLabel, emitSlider);
        addTextFieldListener(spec0ValueLabel, spec0Slider);
        addTextFieldListener(spec1ValueLabel, spec1Slider);


        filterSwatch.setPreferredSize(new Dimension(50, 50));
        filterSwatch.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        filterSwatch.setFocusPainted(false);
        filterSwatch.setContentAreaFilled(true);

        // Clicking the swatch opens a color chooser
        filterSwatch.addActionListener(e -> {
            if (selectedLRTM == null) return;

            // Get stored BGR, convert to RGB
            int rgb = bgrToRgb(selectedLRTM.getColorFilter()) & 0xFFFFFF;
            Color current = new Color(rgb);

            // Show chooser with current color pre-selected
            Color chosen = JColorChooser.showDialog(this, "Choose Filter Color", current);

            if (chosen != null) {
                int chosenRGB = chosen.getRGB() & 0xFFFFFF; // Strip alpha
                selectedLRTM.setColorFilter(rgbToBgr(chosenRGB)); // Store in BGR
                filterSwatch.setBackground(new Color(chosenRGB)); // Update swatch
            }
        });



        shadingSelector.addItemListener(a -> selectedLRTM.setShadingType((LRTMShadingType) a.getItem()));
        
        unk1Selector.addItemListener(a -> {
            if (selectedLRTM != null) {
                selectedLRTM.setUnknownType((LRTMUnkownType) a.getItem());
            }
        });

        GroupLayout groupLayout = new GroupLayout(this);
        groupLayout.setAutoCreateGaps(true); // Adds consistent gaps
        groupLayout.setAutoCreateContainerGaps(true); // Adds margins

        groupLayout.setHorizontalGroup(
                groupLayout.createParallelGroup(Alignment.LEADING)
                    .addGroup(groupLayout.createSequentialGroup()
                        .addComponent(shadingLabel, GroupLayout.PREFERRED_SIZE, 80, GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(shadingSelector, GroupLayout.PREFERRED_SIZE, 150, GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(unk1Label, GroupLayout.PREFERRED_SIZE, 120, GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(unk1Selector, GroupLayout.PREFERRED_SIZE, 150, GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(filterLabel, GroupLayout.PREFERRED_SIZE, 80, GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(filterSwatch, 50, 50, 50)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                    		)

                    // Ambient slider row
                    .addGroup(groupLayout.createSequentialGroup()
                        .addComponent(ambientLabel, GroupLayout.PREFERRED_SIZE, 80, GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(ambientSlider, GroupLayout.PREFERRED_SIZE, 250, GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(ambientValueLabel, GroupLayout.PREFERRED_SIZE, 50, GroupLayout.PREFERRED_SIZE))
                    // Diffusion (Specular) slider row
                    .addGroup(groupLayout.createSequentialGroup()
                        .addComponent(specularLabel, GroupLayout.PREFERRED_SIZE, 80, GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(specularSlider, GroupLayout.PREFERRED_SIZE, 250, GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(specularValueLabel, GroupLayout.PREFERRED_SIZE, 50, GroupLayout.PREFERRED_SIZE))
                    // Emit slider row
                    .addGroup(groupLayout.createSequentialGroup()
                        .addComponent(emitLabel, GroupLayout.PREFERRED_SIZE, 80, GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(emitSlider, GroupLayout.PREFERRED_SIZE, 250, GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(emitValueLabel, GroupLayout.PREFERRED_SIZE, 50, GroupLayout.PREFERRED_SIZE))
                    // Spec0 & Spec1 slider row (Fixed layout)
                    .addGroup(groupLayout.createSequentialGroup()
                        .addComponent(unk2Label, GroupLayout.PREFERRED_SIZE, 80, GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(spec0Slider, GroupLayout.PREFERRED_SIZE, 250, GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(spec0ValueLabel, GroupLayout.PREFERRED_SIZE, 50, GroupLayout.PREFERRED_SIZE))
                    .addGroup(groupLayout.createSequentialGroup()
                        .addComponent(unk3Label, GroupLayout.PREFERRED_SIZE, 80, GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(spec1Slider, GroupLayout.PREFERRED_SIZE, 250, GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(spec1ValueLabel, GroupLayout.PREFERRED_SIZE, 50, GroupLayout.PREFERRED_SIZE))
            );

            groupLayout.setVerticalGroup(
                groupLayout.createSequentialGroup()
                    .addGroup(groupLayout.createParallelGroup(Alignment.BASELINE)
                        .addComponent(shadingLabel)
                        .addComponent(shadingSelector)
                        .addComponent(unk1Label)
                        .addComponent(unk1Selector)
                        .addComponent(filterLabel)
                        .addComponent(filterSwatch, 50, 50, 50))
                    .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                    .addGroup(groupLayout.createParallelGroup(Alignment.CENTER)
                        .addComponent(ambientLabel, GroupLayout.PREFERRED_SIZE, 30, GroupLayout.PREFERRED_SIZE)
                        .addComponent(ambientSlider, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                        .addComponent(ambientValueLabel, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                    .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                    .addGroup(groupLayout.createParallelGroup(Alignment.CENTER)
                        .addComponent(specularLabel, GroupLayout.PREFERRED_SIZE, 30, GroupLayout.PREFERRED_SIZE)
                        .addComponent(specularSlider, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                        .addComponent(specularValueLabel, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                    .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                    .addGroup(groupLayout.createParallelGroup(Alignment.CENTER)
                        .addComponent(emitLabel, GroupLayout.PREFERRED_SIZE, 30, GroupLayout.PREFERRED_SIZE)
                        .addComponent(emitSlider, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                        .addComponent(emitValueLabel, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                    .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                    .addGroup(groupLayout.createParallelGroup(Alignment.CENTER)
                        .addComponent(unk2Label, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                        .addComponent(spec0Slider, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                        .addComponent(spec0ValueLabel, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                    .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                    .addGroup(groupLayout.createParallelGroup(Alignment.CENTER)
                        .addComponent(unk3Label, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                        .addComponent(spec1Slider, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                        .addComponent(spec1ValueLabel, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
            );



        shadingSelector.setModel(new DefaultComboBoxModel<>(LRTMShadingType.values()));
        unk1Selector.setModel(new DefaultComboBoxModel<>(LRTMUnkownType.values()));
        setLayout(groupLayout);


    }

    
    private void updateSliderFromTextField(JTextField field, JSlider slider) {
        try {
            // Replace comma with dot for parsing in all locales
            String input = field.getText().trim().replace(",", ".");
            float value = Float.parseFloat(input);

            if (value < 0.0f || value > 1.0f) throw new NumberFormatException();

            int sliderValue = (int) (value * 1000);

            System.out.println("DEBUG: Text field updated. New value: " + value + ", Slider before: " + slider.getValue());

            if (slider.getValue() != sliderValue) {
                slider.setValue(sliderValue);
                System.out.println("DEBUG: Slider updated to: " + sliderValue);
            } else {
                System.out.println("DEBUG: Slider already at this value.");
            }
        } catch (NumberFormatException ex) {
            System.out.println("DEBUG: Invalid input, resetting.");
            field.setText(String.format("%.3f", slider.getValue() / 1000.0f)); 
        }
    }


    private int bgrToRgb(int bgr) {
        int b = (bgr >> 16) & 0xFF;
        int g = (bgr >> 8) & 0xFF;
        int r = bgr & 0xFF;
        return (r << 16) | (g << 8) | b;
    }

    private int rgbToBgr(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (b << 16) | (g << 8) | r;
    }

    
    public class JHexSpinner extends JSpinner {

        public JHexSpinner() {
            super(new SpinnerNumberModel(0, 0, 0xFFFFFF, 1)); // 0xFFFFFF max

            JSpinner.NumberEditor editor = new JSpinner.NumberEditor(this);
            NumberFormatter formatter = (NumberFormatter) editor.getTextField().getFormatter();

            formatter.setAllowsInvalid(true); // allow typing before validation
            formatter.setCommitsOnValidEdit(true);

            // Always format as 6-digit hex without 0x
            formatter.setFormat(new NumberFormat() {
                @Override
                public StringBuffer format(long number, StringBuffer toAppendTo, FieldPosition pos) {
                    return toAppendTo.append(String.format("%06X", (int) number));
                }

                @Override
                public StringBuffer format(double number, StringBuffer toAppendTo, FieldPosition pos) {
                    return toAppendTo.append(String.format("%06X", (int) number));
                }

                @Override
                public Number parse(String source, ParsePosition pos) {
                    String s = source.trim().replace("#", ""); // allow #RRGGBB too
                    if (s.length() > 6) s = s.substring(s.length() - 6); // limit length
                    try {
                        int value = Integer.parseInt(s, 16) & 0xFFFFFF;
                        pos.setIndex(source.length());
                        return value;
                    } catch (NumberFormatException e) {
                        pos.setErrorIndex(pos.getIndex());
                        return null;
                    }
                }
            });

            setEditor(editor);

            // Commit on Enter or focus lost
            editor.getTextField().addActionListener(e -> commitEditSafe());
            editor.getTextField().addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    commitEditSafe();
                }
            });
        }

        private void commitEditSafe() {
            try {
                commitEdit();
            } catch (java.text.ParseException ignored) {}
        }
    }






    
    @Override
    public void setSelectedFile(Object file) {
        this.selectedLRTM = null;

        if (file instanceof LRTMPayload) {
            this.selectedLRTM = (LRTMPayload) file;
            
            filterSwatch.setBackground(new Color(bgrToRgb(selectedLRTM.getColorFilter()) & 0xFFFFFF));
   
            shadingSelector.setSelectedItem(selectedLRTM.getShadingType());
            unk1Selector.setSelectedItem(selectedLRTM.getUnknownType());

            // Update sliders ONLY IF values changed
            updateSlider(ambientSlider, ambientValueLabel, normalizeRGBA(selectedLRTM.getColor1()));
            updateSlider(specularSlider, specularValueLabel, normalizeRGBA(selectedLRTM.getColor2()));
            updateSlider(spec0Slider, spec0ValueLabel, normalizeRGBA(selectedLRTM.getColor4()));
            updateSlider(spec1Slider, spec1ValueLabel, normalizeRGBA(selectedLRTM.getColor5()));

            // Special handling for emit value
            int packedRGB = selectedLRTM.getColor3() & 0x00FFFFFF; // Keep only RGB
            float newEmitNormalized = normalizeRGB(packedRGB);
            updateSlider(emitSlider, emitValueLabel, newEmitNormalized);
        }
    }

    private void addTextFieldListener(JTextField field, JSlider slider) {
        // Ensure text updates slider when Enter is pressed
        field.addActionListener(e -> updateSliderFromTextField(field, slider));

        // Also trigger update when focus is lost (user clicks elsewhere)
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                field.postActionEvent(); // Manually trigger ActionListener
            }
        });
    }

    private void updateSlider(JSlider slider, JTextField textField, float newValue) {
        int newSliderValue = (int) (newValue * 1000);

        if (slider.getValue() != newSliderValue) { // Only update if different
            slider.setValue(newSliderValue);
            textField.setText(String.format("%.3f", newValue)); // Format text field
        }
    }


    private float normalizeRGB(int rgb) {
        // Extract the Red component (or use Green/Blue since they should be equal)
        int r = (rgb >> 16) & 0xFF; 
        return r / 255.0f; // Convert to 0.0 - 1.0 range
    }
          
    private JSlider createStyledSlider() {
        JSlider slider = new JSlider(0, 1000, 500); // Slider values scaled from 0 to 1000
        slider.setUI(new CustomSliderUI(slider)); // Apply custom UI
        slider.setPaintTicks(false);
        slider.setPaintLabels(false);
        slider.setBackground(new Color(48, 48, 48)); // Background
        return slider;
    }

    private int denormalizeSliderToRGBA(float value) {
        // Convert normalized float (0.0 to 1.0) to packed RGBA (0xRRGGBBAA)
        int component = (int) (value * 255);
        return (component << 24) | (component << 16) | (component << 8) | component; // RGBA
    }

    private float normalizeRGBA(int rgba) {
        // Convert packed RGBA (0xRRGGBBAA) to normalized float (0.0 to 1.0)
        return ((rgba >> 24) & 0xFF) / 255.0f; // Extract one component and normalize
    }

    private int denormalizeSliderToRGB(float value) {
        int component = Math.round(value * 255); // Use `Math.round` instead of casting
        return (component << 16) | (component << 8) | component; // Pack RGB only
    }


    private static class CustomSliderUI extends BasicSliderUI {
        public CustomSliderUI(JSlider slider) {
            super(slider);
        }

        @Override
        public void paintThumb(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setColor(new Color(100, 149, 237)); // Thumb color
            g2d.fillOval(thumbRect.x, thumbRect.y, thumbRect.width, thumbRect.height);
            g2d.dispose();
        }


        @Override
        public void paintTrack(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create();
            int trackHeight = 4;
            int trackY = trackRect.y + (trackRect.height - trackHeight) / 2;

            // Draw filled portion
            g2d.setColor(new Color(0, 122, 204));
            g2d.fillRect(trackRect.x, trackY, thumbRect.x - trackRect.x, trackHeight);

            // Draw unfilled portion
            g2d.setColor(new Color(100, 100, 100));
            g2d.fillRect(thumbRect.x, trackY, trackRect.width - (thumbRect.x - trackRect.x), trackHeight);

            g2d.dispose();
        }
    }
}

