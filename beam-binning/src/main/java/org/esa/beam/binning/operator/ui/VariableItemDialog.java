package org.esa.beam.binning.operator.ui;

import com.bc.ceres.binding.Property;
import com.bc.ceres.binding.PropertyContainer;
import com.bc.ceres.binding.PropertyDescriptor;
import com.bc.ceres.binding.ValidationException;
import com.bc.ceres.binding.Validator;
import com.bc.ceres.swing.TableLayout;
import com.bc.ceres.swing.binding.BindingContext;
import com.bc.ceres.swing.binding.PropertyEditor;
import com.bc.ceres.swing.binding.PropertyEditorRegistry;
import com.bc.ceres.swing.binding.internal.TextComponentAdapter;
import com.bc.ceres.swing.binding.internal.TextFieldEditor;
import com.bc.jexp.ParseException;
import org.esa.beam.binning.operator.VariableConfig;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.dataop.barithm.BandArithmetic;
import org.esa.beam.framework.ui.ModalDialog;
import org.esa.beam.framework.ui.product.ProductExpressionPane;
import org.esa.beam.util.StringUtils;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Tonio Fincke
 */
class VariableItemDialog extends ModalDialog {

    private static final String PROPERTY_VARIABLE_NAME = "variableName";
    private static final String PROPERTY_EXPRESSION = "expression";

    private static int numNewBands = 0;

    @SuppressWarnings("UnusedDeclaration")
    private String variableName; // used in binding
    private String expression = "";

    private final Product contextProduct;
    private final BindingContext bindingContext;


    private VariableConfig variableConfig;

    VariableItemDialog(final Window parent, Product contextProduct) {
        super(parent, "Define new variable", ID_OK_CANCEL, null);
        this.contextProduct = contextProduct;
        bindingContext = createBindingContext();
        makeUI();
    }

    @Override
    protected boolean verifyUserInput() {
        String trimmedExpr = expression.trim();
        String trimmedVarName = variableName.trim();
        if (StringUtils.isNullOrEmpty(trimmedExpr)) {
            JOptionPane.showMessageDialog(getParent(), "The variable could not be created. The expression was empty.");
            return false;
        }
        if (contextProduct.containsBand(trimmedVarName)) {
            String message = String.format("A variable or band with the name '%s' is already defined", trimmedVarName);
            JOptionPane.showMessageDialog(getParent(), message);
            return false;
        }
        try {
            BandArithmetic.getValidMaskExpression(trimmedExpr, new Product[]{contextProduct}, 0, null);
        } catch (ParseException e) {
            String errorMessage = "The variable could not be created.\nThe expression could not be parsed:\n" + e.getMessage(); /*I18N*/
            JOptionPane.showMessageDialog(getParent(), errorMessage);
            return false;
        }
        return true;
    }

    @Override
    protected void onOK() {
        variableConfig = new VariableConfig(variableName.trim(), expression.trim());
        super.onOK();
    }

    VariableConfig getVariableConfig() {
        return variableConfig;
    }

    private BindingContext createBindingContext() {
        final PropertyContainer container = PropertyContainer.createObjectBacked(this);
        final BindingContext context = new BindingContext(container);

        PropertyDescriptor descriptor = container.getDescriptor(PROPERTY_VARIABLE_NAME);
        descriptor.setDisplayName("Name");
        descriptor.setDescription("The name for the new variable.");
        descriptor.setNotEmpty(true);
        descriptor.setValidator(new ProductNodeNameValidator());
        ++numNewBands;
        while (contextProduct.containsRasterDataNode("variable_" + (numNewBands))) {
            ++numNewBands;
        }
        descriptor.setDefaultValue("variable_" + (numNewBands));

        descriptor = container.getDescriptor(PROPERTY_EXPRESSION);
        descriptor.setDisplayName("Band maths expression");
        descriptor.setDescription("Band maths expression");
        descriptor.setNotEmpty(true);

        container.setDefaultValues();

        return context;
    }

    private void makeUI() {
        JButton editExpressionButton = new JButton("Edit Expression...");
        editExpressionButton.setName("editExpressionButton");
        editExpressionButton.addActionListener(createEditExpressionButtonListener());

        final TableLayout tableLayout = new TableLayout(1);
        tableLayout.setTableWeightX(1.0);
        tableLayout.setTableWeightY(0.0);
        tableLayout.setTableAnchor(TableLayout.Anchor.NORTHWEST);
        tableLayout.setTableFill(TableLayout.Fill.HORIZONTAL);
        final JPanel panel = new JPanel(tableLayout);

        JComponent[] variableComponents = createComponents(PROPERTY_VARIABLE_NAME, TextFieldEditor.class);

        final TableLayout variablePanelLayout = new TableLayout(2);
        variablePanelLayout.setTableWeightX(1.0);
        variablePanelLayout.setTableFill(TableLayout.Fill.HORIZONTAL);
        final JPanel variablePanel = new JPanel(variablePanelLayout);

        variablePanel.add(variableComponents[1]);
        variablePanel.add(variableComponents[0]);
        panel.add(variablePanel);

        JLabel expressionLabel = new JLabel("Variable expression:");
        JTextArea expressionArea = new JTextArea();
        expressionArea.setRows(3);
        TextComponentAdapter textComponentAdapter = new TextComponentAdapter(expressionArea);
        bindingContext.bind(PROPERTY_EXPRESSION, textComponentAdapter);
        panel.add(expressionLabel);
        panel.add(expressionArea);
        final TableLayout editExpressionPanelLayout = new TableLayout(2);
        editExpressionPanelLayout.setTableWeightX(0.0);
        final JPanel editExpressionPanel = new JPanel(editExpressionPanelLayout);
        editExpressionPanel.add(editExpressionPanelLayout.createHorizontalSpacer());
        editExpressionPanel.add(editExpressionButton);
        panel.add(editExpressionPanel);
        panel.add(tableLayout.createVerticalSpacer());

        setContent(panel);
    }

    private JComponent[] createComponents(String propertyName, Class<? extends PropertyEditor> editorClass) {
        PropertyDescriptor descriptor = bindingContext.getPropertySet().getDescriptor(propertyName);
        PropertyEditor editor = PropertyEditorRegistry.getInstance().getPropertyEditor(editorClass.getName());
        return editor.createComponents(descriptor, bindingContext);
    }

    private ActionListener createEditExpressionButtonListener() {
        return new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                ProductExpressionPane expressionPane =
                        ProductExpressionPane.createGeneralExpressionPane(new Product[]{contextProduct},
                                                                          contextProduct,
                                                                          null);
                expressionPane.setCode(expression.trim());
                int status = expressionPane.showModalDialog(getJDialog(), "Expression Editor");
                if (status == ModalDialog.ID_OK) {
                    bindingContext.getBinding(PROPERTY_EXPRESSION).setPropertyValue(expressionPane.getCode());
                }
                expressionPane.dispose();
            }
        };
    }

    private class ProductNodeNameValidator implements Validator {

        @Override
        public void validateValue(Property property, Object value) throws ValidationException {
            final String name = (String) value;
            if (contextProduct.containsRasterDataNode(name)) {
                throw new ValidationException("The variable name must be unique.");
            }
        }
    }

}
