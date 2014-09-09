(ns internal.clojure
  (:require [clojure.java.io :as io]
            [clojure.tools.namespace.file :refer [read-file-ns-decl]]
            [dynamo.resource :as resource]
            [dynamo.file :as file]
            [dynamo.node :refer [defnode]]
            [dynamo.env :refer [*current-project*]]
            [plumbing.core :refer [defnk]]
            [eclipse.markers :as markers]
            [service.log :as log])
  (:import [org.eclipse.core.resources IFile]
           [clojure.lang LineNumberingPushbackReader]))

(defrecord UnloadableNamespace [ns-decl]
  resource/IDisposable
  (dispose [this]
    (when (list? ns-decl)
      (remove-ns (second ns-decl)))))

(defnk load-project-file
  [project this g resource]
  (let [ns-decl     (read-file-ns-decl resource)
        source-file (file/eclipse-file resource)]
    (markers/remove-markers source-file)
    (try
      (do
        (binding [*current-project* project]
          (Compiler/load (io/reader resource) (file/local-path resource) (.getName source-file))
          (UnloadableNamespace. ns-decl)))
      (catch clojure.lang.Compiler$CompilerException compile-error
        (markers/compile-error source-file compile-error)
        {:compile-error (.getMessage (.getCause compile-error))}))))

(defnode ClojureSourceNode
  (property resource IFile)
  (output   namespace UnloadableNamespace :cached :on-update load-project-file))
