# Dialog system

## BaseDialog

All dialogs except `MessageDialog` extend `BaseDialog` (`gui/dialog/BaseDialog.java`).

**Opening a dialog**

Call `show()` from any thread. It sets an internal flag consumed on the next frame — safe to call from button callbacks inside `render()`.

**Rendering skeleton**

```java
@Override
public void render() {
    if (beginModal("Title")) {
        // ... widgets ...
        endModal();
    }
}
```

Always call `endModal()` if `beginModal` returned true.

**Sizing variants**

- `beginModal(title)` — 90 % display width, auto height
- `beginModal(title, fixedH)` — 90 % width, fixed height
- `beginModal(title, minH, maxH)` — full control

**Navigating back to a parent dialog**

Override `onClose()` and call the parent's `show()`:

```java
@Override
protected void onClose() {
    BaudBound.getWebhooksDialog().show();
}
```

This fires when the user clicks the X button to close the modal.

## MessageDialog

`MessageDialog` is a standalone class (not a `BaseDialog`). Call:

```java
BaudBound.getMessageDialog().show("Title", "Body text", new DialogButton("OK", optionalCallback));
```

`DialogButton` takes a label and an optional `Runnable` action.

## GuiHelper — list + editor buttons

`GuiHelper.listAndEditorButtons(id, items, selected, fillHeight, onCreate, onEdit, onDuplicate, onDelete, onError)` renders a list box followed by Create / Edit / Duplicate / Delete buttons. See `gui/util/GuiHelper.java` for the full signature.

Table helpers: `keyValueTable(...)` — two overloads, one with plain `inputText` columns and one where column 0 is a combo box. See `GuiHelper.java:122-140`.