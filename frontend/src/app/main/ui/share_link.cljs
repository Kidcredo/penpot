;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) UXBOX Labs SL

(ns app.main.ui.share-link
  (:require
   [app.common.data :as d]
   [app.main.data.modal :as modal]
   [app.main.store :as st]
   [app.main.ui.icons :as i]
   [app.main.worker :as uw]
   [app.util.dom :as dom]
   [app.util.i18n :as i18n :refer [tr]]
   [app.util.keyboard :as kbd]
   [app.util.logging :as log]
   [beicon.core :as rx]
   [rumext.alpha :as mf]))

(log/set-level! :debug)

(mf/defc share-link-dialog
  {::mf/register modal/components
   ::mf/register-as :share-link}
  []
  (let [cancel-fn
        (mf/use-callback
         (fn [event]
           (dom/prevent-default event)
           (st/emit! (modal/hide))))]

    [:div.modal-overlay
     [:div.modal-container.share-link-dialog
      [:div.modal-content
       [:div.title
        [:h2 "Share prototypes"]
        [:div.modal-close-button
         {:on-click cancel-fn} i/close]]

       [:div.link-container
        [:label "Link"]
        [:div.custom-input.with-icon
         [:input {:type "text"}]
         [:div.help-icon i/copy]]

        [:div.hint "Anyone with link will have access"]]]

      [:div.modal-content
       [:div.access-mode
        [:div.title "Can access"]
        [:div.items
         [:div.input-checkbox.check-primary
          [:input {:type "checkbox"}]
          [:label "Viewer mode" [:span.hint "(default)"]]]

         [:div.input-checkbox.check-primary.disabled
          [:input.check-primary.input-checkbox {:type "checkbox"}]
          [:label "Workspace"]]]]

       [:div.view-mode
        [:div.title "Can view"]
        [:div.items
         [:div.input-radio.radio-primary
          [:input {:type "radio"}]
          [:label "All pages"]]

         [:div.input-radio.radio-primary
          [:input {:type "radio"}]
          [:label "Only this page"]]

         [:div.input-radio.radio-primary
          [:input.check-primary.input-checkbox {:type "checkbox"}]
          [:label "Selected pages"]]]]

       [:ul.pages-selection
        (for [i (range 9)]
          [:li.input-checkbox.check-primary {:key i}
           [:input {:type "checkbox"}]
           [:label (str "Page " i)]])]]


      [:div.modal-footer
       [:input.btn-primary.btn-get-link
        {:type "button"
         :class "primary"
         :value "Get link"}]]


      ]]))



