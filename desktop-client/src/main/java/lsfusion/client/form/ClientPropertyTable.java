package lsfusion.client.form;

import com.google.common.base.Preconditions;
import lsfusion.base.BaseUtils;
import lsfusion.base.SystemUtils;
import lsfusion.client.SwingUtils;
import lsfusion.client.form.cell.CellTableInterface;
import lsfusion.client.form.cell.ClientAbstractCellEditor;
import lsfusion.client.form.cell.ClientAbstractCellRenderer;
import lsfusion.client.form.dispatch.EditPropertyDispatcher;
import lsfusion.client.logics.ClientGroupObjectValue;
import lsfusion.client.logics.ClientPropertyDraw;
import lsfusion.client.logics.classes.ClientStringClass;
import lsfusion.client.logics.classes.ClientType;
import lsfusion.interop.KeyStrokes;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.text.ParseException;
import java.util.EventObject;

import static lsfusion.client.form.EditBindingMap.*;

public abstract class ClientPropertyTable extends JTable implements TableTransferHandler.TableInterface, CellTableInterface, EditPropertyHandler {
    private final EditPropertyDispatcher editDispatcher;
    protected final EditBindingMap editBindingMap = new EditBindingMap();
    private final CellTableContextMenuHandler contextMenuHandler = new CellTableContextMenuHandler(this);
    
    protected final ClientFormController form;

    protected EventObject editEvent;
    protected int editRow;
    protected int editCol;
    protected ClientType currentEditType;
    protected Object currentEditValue;
    protected boolean editPerformed;
    protected boolean commitingValue;

    protected ClientPropertyTable(TableModel model, ClientFormController form) {
        super(model);
        
        this.form = form;

        editDispatcher = new EditPropertyDispatcher(this, form.getDispatcherListener());

        SwingUtils.setupClientTable(this);

        setDefaultRenderer(Object.class, new ClientAbstractCellRenderer());
        setDefaultEditor(Object.class, new ClientAbstractCellEditor(this));

        initializeActionMap();

        contextMenuHandler.install();
    }

    private void initializeActionMap() {
        //  Have the enter key work the same as the tab key
        InputMap im = getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        im.put(KeyStrokes.getEnter(), im.get(KeyStrokes.getTab()));
    }

    public ClientType getCurrentEditType() {
        return currentEditType;
    }

    public Object getCurrentEditValue() {
        return currentEditValue;
    }

    public boolean editCellAt(int row, int column, EventObject e) {
        if (!form.commitCurrentEditing()) {
            return false;
        }

        if (row < 0 || row >= getRowCount() || column < 0 || column >= getColumnCount()) {
            return false;
        }

        ClientPropertyDraw property = getProperty(row, column);
        ClientGroupObjectValue columnKey = getColumnKey(row, column);

        String actionSID = getPropertyEditActionSID(e, property, editBindingMap);

        if (actionSID == null) {
            return false;
        }

        if (isEditableAwareEditEvent(actionSID) && !isCellEditable(row, column)) {
            return false;
        }

        quickLog("formTable.editCellAt: " + e);

        editRow = row;
        editCol = column;
        editEvent = e;
        commitingValue = false;

        //здесь немного запутанная схема...
        //executePropertyEditAction возвращает true, если редактирование произошло на сервере, необязательно с вводом значения...
        //но из этого editCellAt мы должны вернуть true, только если началось редактирование значения
        editPerformed = editDispatcher.executePropertyEditAction(property, columnKey, actionSID, getValueAt(row, column), editEvent);
        return editorComp != null;
    }

    public abstract int getCurrentRow();

    public boolean requestValue(ClientType valueType, Object oldValue) {
        quickLog("formTable.requestValue: " + valueType);

        //пока чтение значения можно вызывать только один раз в одном изменении...
        //если получится безусловно задержать фокус, то это ограничение можно будет убрать
        Preconditions.checkState(!commitingValue, "You can request value only once per edit action.");

        currentEditType = valueType;
        currentEditValue = oldValue;

        if (!super.editCellAt(editRow, editCol, editEvent)) {
            return false;
        }

        prepareTextEditor();

        editorComp.requestFocusInWindow();

        form.setCurrentEditingTable(this);

        return true;
    }

    void prepareTextEditor() {
        ClientPropertyDraw property = getProperty(editRow, editCol);
        if (editorComp instanceof JTextComponent) {
            JTextComponent textEditor = (JTextComponent) editorComp;
            if(!property.notSelectAll) {
                textEditor.selectAll();
            }
            if (getProperty(editRow, editCol).clearText) {
                textEditor.setText("");
            }
        } else if (editorComp instanceof ClientPropertyTableEditorComponent) {
            ClientPropertyTableEditorComponent propertyTableEditorComponent = (ClientPropertyTableEditorComponent) editorComp;
            propertyTableEditorComponent.prepareTextEditor(property.clearText, !property.notSelectAll);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public Component prepareEditor(TableCellEditor editor, int row, int column) {
        Component component = super.prepareEditor(editor, row, column);
        if (component instanceof JComponent) {
            JComponent jComponent = (JComponent) component;
            // у нас есть возможность редактировать нефокусную таблицу, и тогда после редактирования фокус теряется,
            // поэтому даём возможность FocusManager'у самому поставить фокус
            if (!isFocusable() && jComponent.getNextFocusableComponent() == this) {
                jComponent.setNextFocusableComponent(null);
                return component;
            }
        }
        return component;
    }

    public void updateEditValue(Object value) {
        setValueAt(value, editRow, editCol);
    }

    @Override
    public void editingStopped(ChangeEvent e) {
        TableCellEditor editor = getCellEditor();
        if (editor != null) {
            Object value = editor.getCellEditorValue();
            internalRemoveEditor();
            commitValue(value);
        }
    }

    private void commitValue(Object value) {
        quickLog("formTable.commitValue: " + value);
        commitingValue = true;
        editDispatcher.commitValue(value);
        form.clearCurrentEditingTable(this);
    }

    @Override
    public void editingCanceled(ChangeEvent e) {
        quickLog("formTable.cancelEdit");
        internalRemoveEditor();
        editDispatcher.cancelEdit();
        form.clearCurrentEditingTable(this);
    }

    @SuppressWarnings("deprecation")
    protected void internalRemoveEditor() {
        Component nextComp = null;
        if (editorComp instanceof JComponent) {
            nextComp = ((JComponent) editorComp).getNextFocusableComponent();
        }

        //copy/paste из JTable
        // изменён, чтобы не запрашивать фокус обратно в таблицу,
        // потому что на самом деле нам надо, чтобы он переходил на editorComponent.getNextFocusableComponent()
        // в обычных случаях - это и будет таблица, но при редактировании по хоткею - предыдущий компонент,
        // а в случае начала редактирование новой таблицы - эта новая таблица
        TableCellEditor editor = getCellEditor();
        if(editor != null) {
            editor.removeCellEditorListener(this);
            if (editorComp != null) {
                remove(editorComp);
            }

            Rectangle cellRect = getCellRect(editingRow, editingColumn, false);

            setCellEditor(null);
            setEditingColumn(-1);
            setEditingRow(-1);
            editorComp = null;

            repaint(cellRect);
        }
        super.removeEditor();

        if (nextComp != null) {
            nextComp.requestFocusInWindow();
        }
    }

    @Override
    public void removeEditor() {
        // removeEditor иногда вызывается напрямую, поэтому вызываем cancelCellEditing сами
        TableCellEditor cellEditor = getCellEditor();
        if (cellEditor != null) {
            cellEditor.cancelCellEditing();
        }
    }

    @Override
    public void columnMarginChanged(ChangeEvent e) {
        //copy/paste from JTable minus removeEditor...
        TableColumn resizingColumn = (tableHeader == null) ? null : tableHeader.getResizingColumn();
        if (resizingColumn != null && autoResizeMode == AUTO_RESIZE_OFF) {
            resizingColumn.setPreferredWidth(resizingColumn.getWidth());
        }
        resizeAndRepaint();
    }

    protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
        editPerformed = false;
        boolean consumed = e.isConsumed() || super.processKeyBinding(ks, e, condition, pressed);
        return consumed || editPerformed;
    }

    @Override
    protected void processKeyEvent(final KeyEvent e) {
        int row = getCurrentRow();
        int column = getSelectedColumn();
        if (row >= 0 && row < getRowCount() && column >= 0 && column < getColumnCount()) {
            ClientPropertyDraw property = getProperty(row, column);
            ClientGroupObjectValue columnKey = getColumnKey(row, column);

            String keyPressedActionSID = getPropertyKeyPressActionSID(e, property);
            if (keyPressedActionSID != null) {
                editDispatcher.executePropertyEditAction(property, columnKey, keyPressedActionSID, getValueAt(row, column), editEvent);
            }
        }
        
        SwingUtils.getAroundTooltipListener(this, e, new Runnable() {
            @Override
            public void run() {
                ClientPropertyTable.super.processKeyEvent(e);
            }
        });
    }

    @Override
    public synchronized void addMouseListener(MouseListener listener) {
        //подменяем стандартный MouseListener
        if (listener != null && ("javax.swing.plaf.basic.BasicTableUI$Handler".equals(listener.getClass().getName()) ||
                "com.apple.laf.AquaTableUI$MouseInputHandler".equals(listener.getClass().getName()))) {
            listener = new ClientPropertyTableUIHandler(this);
        }
        super.addMouseListener(listener);
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        java.awt.Point p = e.getPoint();
        int rowIndex = rowAtPoint(p);
        int colIndex = columnAtPoint(p);

        if (rowIndex != -1 && colIndex != -1) {
            ClientPropertyDraw cellProperty = getProperty(rowIndex, colIndex);
            
            // todo: временно отключил тултипы для richText'а для Java старше 8. часто вылетает (особенно при вставке из Word). следует убрать проверку после перехода на Java 8:
            // https://bugs.openjdk.java.net/browse/JDK-8034955
            Double javaVersion = SystemUtils.getJavaSpecificationVersion();
            if ((javaVersion == null || javaVersion < 1.8) && cellProperty.baseType instanceof ClientStringClass && ((ClientStringClass) cellProperty.baseType).rich) {
                return null;
            }
            
            if (!cellProperty.echoSymbols) {
                Object value = getValueAt(rowIndex, colIndex);
                if (value != null) {
                    if (value instanceof Double) {
                        value = (double) Math.round(((Double) value) * 1000) / 1000;
                    }

                    String formattedValue;
                    try {
                        formattedValue = cellProperty.formatString(value);
                    } catch (ParseException e1) {
                        formattedValue = String.valueOf(value);
                    }

                    if (!BaseUtils.isRedundantString(formattedValue)) {
                        return SwingUtils.toMultilineHtml(formattedValue, createToolTip().getFont());
                    }
                } else if (cellProperty.isEditableNotNull()) {
                    return PropertyRenderer.REQUIRED_STRING;
                }
            }
        }
        return null;
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        //JViewport по умолчанию использует тупо константу - переопределяем это поведение
        return getPreferredSize();
    }

    protected void quickLog(String msg) {
//        if (form.isDialog()) {
//            return;
//        }
//        System.out.println("-------------------------------------------------");
//        System.out.println(this + ": ");
//        System.out.println("    " + msg);
//        ExceptionUtils.dumpStack();
    }

    public EditPropertyDispatcher getEditPropertyDispatcher() {
        return editDispatcher;
    }
}
