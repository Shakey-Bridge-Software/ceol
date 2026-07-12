(ns ceol.web.handlers.editor
  "Editor and inline-field action handlers. Manages the ABC editor panel
   (toggle, live update, escape-to-blur) and inline header field editing
   (enter edit mode, cancel, confirm/cancel via key)."
  (:require [ceol.web.state :as state]
            [ceol.web.persist :as persist]))

(defn- focus-editor-soon! []
  (js/requestAnimationFrame
   (fn []
     (when-let [el (js/document.querySelector ".editor-textarea")]
       (.focus el)))))

(defn- editable-tune? [state]
  (let [tune-id (:selected-tune-id state)]
    (and tune-id
         (not (:session-mode? state))
         (not (state/catalog-tune? tune-id)))))

(defn toggle! [_args]
  (let [state @state/app-state]
    (when (editable-tune? state)
      (let [opening? (not (:editor-open? state))]
        (swap! state/app-state assoc :editor-open? opening?)
        (when opening? (focus-editor-soon!))))))

(defn open! [_args]
  (let [state @state/app-state]
    (when (and (editable-tune? state) (not (:editor-open? state)))
      (swap! state/app-state assoc :editor-open? true)
      (focus-editor-soon!))))

(defn update! [[tune-id new-val]]
  (when (string? new-val)
    (swap! state/app-state assoc-in [:abc-edits tune-id] new-val)
    (persist/schedule-save!)))

(defn keydown! [[key]]
  (when (= key "Escape")
    (when-let [el (js/document.querySelector ".editor-textarea")]
      (.blur el))))

(defn field-edit! [[field]]
  (swap! state/app-state assoc :editing-field field))

(defn field-cancel! [_args]
  (swap! state/app-state assoc :editing-field nil))

(defn field-keydown! [[key]]
  (case key
    "Enter" (when-let [el (js/document.querySelector ".inline-edit-title")]
              (.blur el))
    "Escape" (swap! state/app-state assoc :editing-field nil)
    nil))
