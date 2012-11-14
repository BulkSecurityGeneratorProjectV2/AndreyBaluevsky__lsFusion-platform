package platform.gwt.form.shared.view.grid.editor;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import platform.gwt.form.shared.view.grid.EditEvent;

public interface GridCellEditor {

    void render(Cell.Context context, Object value, SafeHtmlBuilder sb);

    void onBrowserEvent(Cell.Context context, Element parent, Object value, NativeEvent event, ValueUpdater<Object> valueUpdater);

    void startEditing(EditEvent editEvent, Cell.Context context, Element parent, Object oldValue);

    boolean resetFocus(Cell.Context context, Element parent, Object value);
}
