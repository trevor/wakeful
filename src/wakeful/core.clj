(ns wakeful.core
  (:use compojure.core
        [useful :only [update into-map verify]]
        [ring.middleware.params :only [wrap-params]]
        [clout.core :only [route-compile]])
  (:require [clj-json.core :as json]))

(defn resolve-method [ns-prefix type method]
  (let [ns     (symbol (if type (str (name ns-prefix) "." (name type)) ns-prefix))
        method (symbol (if (string? method) method (apply str method)))]
    (try (require ns)
         (ns-resolve ns method)
         (catch java.io.FileNotFoundException e))))

(defn node-type [^String id]
  (let [i (.indexOf id "-")]
    (when (not= -1 i)
      (.substring id 0 i))))

(defn node-number [^String id & [node-type]]
  (let [[type num] (.split id "-")]
    (verify (or (nil? node-type) (= node-type type))
            (format "node-id %s is not of type %s" id node-type))
    (Long/parseLong num)))

(defn- assoc-type [route-params]
  (assoc route-params :type (node-type (:id route-params))))

(defn- wrap-json [handler]
  (fn [{body :body :as request}]
    (let [body (when body (json/parse-string (slurp body)))]
      (when-let [response (handler (assoc request :body body))]
        (-> response
            (update :body json/generate-string)
            (assoc-in [:headers "Content-Type"] "application/json; charset=utf-8"))))))

(defn- ns-router [ns-prefix wrapper & [method-suffix]]
  (fn [{{:keys [method type id]} :route-params :as request}]
    (when-let [method (resolve-method ns-prefix type [method method-suffix])]
      (if (and wrapper (not (:no-wrap (meta method))))
        ((wrapper method) request)
        (method request)))))

(defn route [pattern]
  (route-compile pattern {:id #"\w+-\d+" :type #"\w+" :method #"[\w-]+"}))

(defn- read-routes [read]
  (routes (GET (route "/:id") {:as request}
               (read (-> request
                         (update :route-params assoc-type)
                         (assoc-in [:route-params :method] "node"))))

          (GET (route "/:id/:method") {:as request}
               (read (update request :route-params assoc-type)))

          (GET (route "/:id/:method/*") {:as request}
               (read (update request :route-params assoc-type)))

          (GET (route "/:type/:method") {:as request}
               (read request))

          (GET (route "/:type/:method/*") {:as request}
               (read request))

          (GET (route "/:method") {:as request}
               (read request))))

(defn- write-routes [write]
  (routes (POST (route "/:id/:method") {:as request}
                (write (update request :route-params assoc-type)))

          (POST (route "/:id/:method/*") {:as request}
                (write (update request :route-params assoc-type)))

          (POST (route "/:type/:method") {:as request}
                (write request))

          (POST (route "/:type/:method/*") {:as request}
                (write request))

          (POST (route "/:method") {:as request}
                (write request))))

(def *bulk* nil)

(defn- bulk [request-method handler wrapper]
  ((or wrapper identity)
   (fn [{:keys [body query-params]}]
     (binding [*bulk* true]
       {:body (doall
               (map (fn [[uri params body]]
                      (:body (handler
                              {:request-method request-method
                               :uri            uri
                               :query-params   (merge query-params (or params {}))
                               :body           body})))
                    body))}))))

(defn- bulk-routes [read write opts]
  (let [bulk-read  (bulk :get  read  (:bulk-read  opts))
        bulk-write (bulk :post write (:bulk-write opts))]
    (routes (POST "/bulk-read" {:as request}
                  (bulk-read request))
            (POST "/bulk-write" {:as request}
                  (bulk-write request)))))

(defn wakeful [ns-prefix & opts]
  (let [opts  (into-map opts)
        read  (read-routes  (ns-router ns-prefix (:read  opts)))
        write (write-routes (ns-router ns-prefix (:write opts) (or (:write-suffix opts) "!")))
        bulk  (bulk-routes read write opts)]
    (-> (routes read bulk write)
        wrap-params
        wrap-json)))