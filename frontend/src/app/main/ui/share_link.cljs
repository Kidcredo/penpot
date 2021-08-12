;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) UXBOX Labs SL

(ns app.main.ui.share-link
  (:require
   [app.common.data :as d]
   [app.config :as cf]
   [app.main.data.common :as dc]
   [app.main.data.messages :as dm]
   [app.main.data.modal :as modal]
   [app.main.refs :as refs]
   [app.main.store :as st]
   [app.main.ui.icons :as i]
   [app.main.worker :as uw]
   [app.util.dom :as dom]
   [app.util.i18n :as i18n :refer [tr]]
   [app.util.keyboard :as kbd]
   [app.util.logging :as log]
   [app.util.router :as rt]
   [app.util.webapi :as wapi]
   [beicon.core :as rx]
   [potok.core :as ptk]
   [rumext.alpha :as mf]))

(log/set-level! :debug)

(defn prepare-params
  [{:keys [access view pages]}]
  {:pages pages
   :flags (-> #{}
              (into (map #(str "access-" %)) access)
              (into (map #(str "view-" %)) [view]))})

(mf/defc share-link-dialog
  {::mf/register modal/components
   ::mf/register-as :share-link}
  [{:keys [file page]}]
  (let [slinks  (mf/deref refs/share-links)
        router  (mf/deref refs/router)
        route   (mf/deref refs/route)

        link    (mf/use-state nil)
        confirm (mf/use-state false)

        opts    (mf/use-state
                 {:view "this-page"
                  :access #{"viewer"}
                  :pages #{(:id page)}})

        close
        (fn [event]
          (dom/prevent-default event)
          (st/emit! (modal/hide)))

        select-view-mode
        (fn [mode]
          (reset! confirm false)
          (swap! opts
                 (fn [state]
                   (-> state
                       (assoc :view mode)
                       (cond-> (= mode "this-page") (assoc :pages #{(:id page)}))
                       (cond-> (= mode "all-pages") (assoc :pages (into #{} (get-in file [:data :pages]))))))))

        mark-checked-page
        (fn [event id]
          (let [target   (dom/get-target event)
                checked? (.-checked ^js target)]
            (reset! confirm false)
            (swap! opts update :pages
                   (fn [pages]
                     (if checked?
                       (conj pages id)
                       (disj pages id))))))

        create-link
        (fn [_]
          (let [params (prepare-params @opts)
                params (assoc params :file-id (:id file))]
            (st/emit! (dc/create-share-link params))))


        copy-link
        (fn [_]
          (wapi/write-to-clipboard @link)
          (st/emit! (dm/show {:type :info
                              :content "Link copied successfuly!"
                              :timeout 3000})))

        try-delete-link
        (fn [_]
          (reset! confirm true))

        delete-link
        (fn [_]
          (let [params (prepare-params @opts)
                slink  (d/seek #(= (:flags %) (:flags params)) slinks)]
            (reset! confirm false)
            (st/emit! (dc/delete-share-link slink)
                      (dm/show {:type :info
                                :content "Link deleted"
                                :timeout 3000}))))
        ]

    (mf/use-effect
     (mf/deps file slinks @opts)
     (fn []
       (let [params (prepare-params @opts)
             slink  (d/seek #(= (:flags %) (:flags params)) slinks)
             href   (when slink
                      (let [pparams (:path-params route)
                            qparams (-> (:query-params route)
                                        (assoc :token (:id slink))
                                        (assoc :index "0"))

                            href    (rt/resolve router :viewer pparams qparams)]
                        (assoc cf/public-uri :fragment href)))]
         (reset! link (some-> href str)))))


    (prn "KAKAK" @link)

    [:div.modal-overlay
     [:div.modal-container.share-link-dialog
      [:div.modal-content
       [:div.title
        [:h2 "Share prototypes"]
        [:div.modal-close-button
         {:on-click close :title "Close"} i/close]]

       [:div.share-link-section
        [:label "Link"]
        [:div.custom-input.with-icon
         [:input {:type "text" :value (or @link "") :read-only true}]
         [:div.help-icon {:title "Copy" :on-click copy-link} i/copy]]

        [:div.hint "Anyone with link will have access"]]]

      [:div.modal-content
       (let [mode (:access @opts)]
         [:div.access-mode
          [:div.title "Can access"]
          [:div.items
           [:div.input-checkbox.check-primary
            [:input {:type "checkbox"
                     :default-checked (contains? mode "viewer")}]
            [:label "Viewer mode" [:span.hint "(default)"]]]

           [:div.input-checkbox.check-primary.disabled
            [:input.check-primary.input-checkbox {:type "checkbox"}]
            [:label "Workspace" ]]]])

       (let [mode (:view @opts)]
         [:*
          [:div.view-mode
           [:div.title "Can view"]
           [:div.items
            [:div.input-radio.radio-primary
             [:input {:type "radio"
                      :id "view-all-pages"
                      :checked (= "all-pages" mode)
                      :name "view-mode"
                      :on-change #(select-view-mode "all-pages")}]
             [:label {:for "view-all-pages"} "All pages"]]

            [:div.input-radio.radio-primary
             [:input {:type "radio"
                      :id "view-this-page"
                      :name "view-mode"
                      :checked (= "this-page" mode)
                      :on-change #(select-view-mode "this-page")}]
             [:label {:for "view-this-page"} "Only this page"]]

            [:div.input-radio.radio-primary
             [:input {:type "radio"
                      :id "view-selected-pages"
                      :name "view-mode"
                      :checked (= "selected-pages" mode)
                      :on-change #(select-view-mode "selected-pages")}]
             [:label {:for "view-selected-pages"} "Selected pages"]]]]

          (when (= "selected-pages" mode)
            (let [pages   (->> (get-in file [:data :pages])
                               (map #(get-in file [:data :pages-index %])))
                  selected (:pages @opts)]
              [:ul.pages-selection
               (for [page pages]
                 [:li.input-checkbox.check-primary {:key (str (:id page))}
                  [:input {:type "checkbox"
                           :id (str "page-" (:id page))
                           :on-change #(mark-checked-page % (:id page))
                           :checked (contains? selected (:id page))}]
                  [:label {:for (str "page-" (:id page))} (:name page)]])]))])]

      [:div.modal-footer
       (cond
         (true? @confirm)
         [:div.confirm-dialog
          [:div.description
           "Are you sure you want to remove this link? If you do it, it's no longer be available for anyone"]
          [:div.actions
           [:input.btn-secondary
            {:type "button"
             :on-click #(reset! confirm false)
             :value "Cancel"}]
           [:input.btn-warning
            {:type "button"
             :on-click delete-link
             :value "Remove link"}]]]

         (some? @link)
         [:input.btn-secondary
          {:type "button"
           :class "primary"
           :on-click try-delete-link
           :value "Remove link"}]

         :else
         [:input.btn-primary
          {:type "button"
           :class "primary"
           :on-click create-link
           :value "Get link"}])]

      ]]))



