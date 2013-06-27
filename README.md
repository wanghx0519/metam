# metam

A meta modeling facility for textual model representation with Clojure.

[![Build Status](https://travis-ci.org/friemen/metam.png?branch=master)](https://travis-ci.org/friemen/metam)

## Usage

Include in your project.clj the following dependency

    [metam/core "1.0.3"]

There are only a few API functions:

 * **defmetamodel** -- Macro to define a new metamodel consisting of a hierarchy,
 a map `{type-keyword -> {attr-keyword -> predicate-vector}}` and a defaults
 function var. The defaults function is usually a multimethod that takes
 three arguments: the model element, the type-keyword and the attr-keyword.
 It is invoked for each attribute whose value is nil, and is expected to return
 a corresponding default value.
 * **pr-model** -- To get a (human) readable representation of an instantiated
 model m use `(pr-model m)` in the REPL.
 * **metatype** -- Returns the type-keyword of a model element.

For an example see the wsdl [metamodel](samples/src/samples/wsdl/metamodel.clj)
and [model](samples/src/samples/wsdl/model.clj) namespaces.


## Motivation by examples

If you want to describe the structure of UI forms, entities,
service interfaces, state machines or other typical elements
of an enterprise application in a DSL like manner, you could
do that easily by just using regular Clojure maps.

Those maps would form an in-memory **model**.

In fact I started creating models in Clojure by introducing 
map-creating factory functions that I used in a nested fashion 
to provide the look as shown below:

Here's the Clojure textual representation of a **UI panel** model:

```clojure
(def p (panel "sample" :lygeneral "flowy, fill" :components [
     (panel "details" :lygeneral "ins 0, wrap 2" :components [
        (dropdownlist "salutation" :initial-items salutations :formatter #(str % "..."))
        (textfield "firstname")
        (textfield "lastname" :required true)
        (textfield "birthday" :format date)
        (textfield "age" :format number :required true)
        (textfield "height" :format number2)
        (checkbox "male")
        (radiogroup "gender" :buttons [ :male :female (radiobutton "other" :text "OTHER") ])
      ])
      (table "addresses" :lyhint "span, grow" :columns [
        (column "firstname" :title "First name" :editable true)
        (column "lastname" :getter-fn #(:lastname %) :editable true)
        (column "street" :getter-fn #(:street %) :setter-fn #(assoc % :street %2))
      ])
      (panel "actions" :lygeneral "ins 0" :lyhint "right, span" :components [
        (button "delete" :action-fn delete)                                                                    
        (button "ok" :action-fn ok)
        (button "cancel")
      ])
   ])
)
```

And a Clojure representation of a **service interface** would be something like this:

```clojure
(def string (simpletype "string"))
(def date (simpletype "date"))

(def address
     (complextype "Address" :elements
                  [(one "street" string)
                   (one "zipcode" string)
                   (one "city" string)]))

(def person
     (complextype "Person" :elements
                  [(one "firstname" string)
                   (one "lastname" string)
                   (one "birthday" date)
                   (many "addresses" address)]))

(def s
     (service "Bar" :operations
              [(op "callMe1"
                   :in-elements [(one "a" address)
                                 (one "t" string)]
                   :out-elements [(one "s" string)])
               (op "callMe2"
                   :in-elements [(one "p" person)])]))
```

To prevent me from creating useless models, a little bit of validation 
as part of these factory functions would be nice.
And yes, it would be handy if model elements would be complemented
with default values to use convention-over-configuration and make
textual models less noisy.

If you'd build more than one of those simple DSLs yourself then you'll see
a lot of repetition (factory functions, validation, defaults provision).
**metam** simply factors these common things into a library with a
defmetamodel macro. So the meta model for the service interface as shown
above would look like this:

```clojure
(defmetamodel wsdl
  (-> (make-hierarchy)
      (derive ::complextype ::datatype)
      (derive ::simpletype ::datatype))
  
  {; a data structure
   ::complextype  {:elements [(type-of? ::e)]}
   
   ; a primitive type (like string, number, date)
   ::simpletype   {}
   
   ; an element of a complextype
   ::e            {:type [required (type-of? ::datatype)]
                   :mult [(value-of? :one :many)]}

   ; a web service                         
   ::service      {:operations [(type-of? ::op)]}
   
   ; a service operation
   ::op           {:in-elements [(type-of? ::e)]
                   :out-elements [(type-of? ::e)]}
   }
  #'no-defaults)


;; Define shortcut functions

(defn one [name type]
  (e name :type type :mult :one))

(defn many [name type]
  (e name :type type :mult :many))
```

## What the macro does

The **defmetamodel** creates a factory function for every meta type
(like ::complextype, ::e or ::service). The functions specified in the
vectors following the attribute keywords are predicates that are used
for validation.
Each factory function validates input and provides default values (if
a corresponding function var was given).

An invocation of macroexpand-1 on a defmetamodel expression outputs:

```clojure
(do (def wsdl-hierarchy
      (-> (make-hierarchy)
          (derive :wsdl/complextype :wsdl/datatype)
          (derive :wsdl/simpletype :wsdl/datatype)))
    (def wsdl
      {:hierarchy wsdl-hierarchy,
       :types
       {:wsdl/complextype
        {:elements [(type-of? :wsdl/e)]},
        :wsdl/simpletype
        {},
        :wsdl/e
        {:type [required (type-of? :wsdl/datatype)],
         :mult [(value-of? :one :many)]},
        :wsdl/service
        {:operations [(type-of? :wsdl/op)]},
        :wsdl/op
        {:in-elements [(type-of? :wsdl/e)],
         :out-elements [(type-of? :wsdl/e)]}},
       :default-fn-var #'no-defaults})
    (def complextype (wsdl/instance-factory wsdl :wsdl/complextype))
    (def simpletype (wsdl/instance-factory wsdl :wsdl/simpletype))
    (def e (wsdl/instance-factory wsdl :wsdl/e))
    (def service (wsdl/instance-factory wsdl :wsdl/service))
    (def op (wsdl/instance-factory wsdl :wsdl/op)))
```

## How to add default values

The following snippet shows another example of a metamodel that also
supports default values:

```clojure
(ns visuals.forml
  (:use [metam.core]))

(declare default-value)

(defmetamodel forml
  (-> (make-hierarchy)
      (derive ::panel ::component)
      (derive ::panel ::growing)
      (derive ::widget ::component)
      (derive ::textfield ::labeled)
      (derive ::textfield ::widget)
      (derive ::label ::widget)
      (derive ::button ::widget))
  {::panel       {:lygeneral [string?]
                  :lycolumns [string?]
                  :lyrows [string?]
                  :lyhint [string?]
                  :components [(type-of? ::component)]}
   ::label       {:text [string?]
                  :lyhint [string?]}
   ::textfield   {:label [string?]
                  :lyhint [string?]
                  :labelyhint [string?]}
   ::button      {:text [string?]
                  :lyhint [string?]}}
  #'default-value)


(defmulti default-value
  (fn [spec type-keyword attr-keyword]
    [type-keyword attr-keyword])
  :hierarchy #'forml-hierarchy)

(defmacro ^:private defdefault
  [dispatch-value & forms]
  (let [args ['spec 'tk 'ak]]
    `(defmethod default-value ~dispatch-value
     ~args
     ~@forms)))

(defdefault :default                     nil)
(defdefault [::widget :lyhint]           "")
(defdefault [::growing :lyhint]          "grow")
(defdefault [::panel :lyrows]            "")
(defdefault [::panel :lycolumns]         "")
(defdefault [::labeled :labelyhint]      "")
(defdefault [::textfield :label]         (:name spec))
(defdefault [::button :text]             (:name spec))
```

The default-value function is a multimethod that takes three arguments:
the model element, the type-keyword and the attr-keyword. It returns the
default value. It is invoked for each attribute whose value is nil.
The defdefault macro is used here only to make the actual mapping between
the dispatch-value and the expression yielding the default value more concise.


# License

Copyright 2013 F.Riemenschneider

Distributed under the Eclipse Public License, the same as Clojure.
