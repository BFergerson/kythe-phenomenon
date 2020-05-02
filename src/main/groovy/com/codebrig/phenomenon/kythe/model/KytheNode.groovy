package com.codebrig.phenomenon.kythe.model

import com.google.devtools.kythe.util.KytheURI
import groovy.transform.Canonical

/**
 * todo: description
 *
 * @version 0.1
 * @since 0.1
 * @author <a href="mailto:brandon@srcpl.us">Brandon Fergerson</a>
 */
@Canonical
class KytheNode implements Serializable {

    boolean isFile
    boolean isFunction
    KytheURI uri
    String signatureSalt
    String context
    String identifier
    KytheNode parentNode
    final List<String> params = new ArrayList<>()

    void addParam(int position, KytheURI identifier) {
        params[position] = identifier.toString()
    }

    String getQualifiedName(KytheIndex index) {
        if (isFile) {
            return context + identifier
        } else {
            def paramStr = ""
            if (!params.isEmpty()) {
                for (int i = 0; i < params.size(); i++) {
                    def param = params.get(i)
                    paramStr += index.paramToTypeMap.get(param)
                    if ((i + 1) < params.size()) {
                        paramStr += ","
                    }
                }
            }

            if (parentNode?.isFile && context.contains(parentNode.context)) {
                if (identifier.contains(")")) {
                    //v0.4.4 names
                    def qualifiedClassName = parentNode.getQualifiedName(index)
                    if (identifier.contains("<init>")) {
                        return qualifiedClassName + qualifiedClassName.substring(qualifiedClassName.lastIndexOf(".")) + "()"
                    }
                    return qualifiedClassName + "." + identifier.substring(0, identifier.indexOf("(")) + "($paramStr)"
                } else {
                    //use parent qualified name as context
                    return parentNode.getQualifiedName(index) + ".$identifier($paramStr)"
                }
            } else {
                if (identifier.contains(")")) {
                    if (identifier.contains("<init>")) {
                        return context + context.substring(context.substring(0, context.length() - 1).lastIndexOf(".") + 1, context.length() - 1) + "()"
                    }
                    if (identifier.substring(0, identifier.indexOf(")") + 1).contains(";")) {
                        def params = identifier.substring(identifier.indexOf("(") + 1, identifier.indexOf(")")).split(";")
                        String actualParamStr = ""
                        params.each {
                            actualParamStr += it.substring(1).replace("/", ".")
                        }
                        return context + identifier.substring(0, identifier.indexOf("(")) + "($actualParamStr)"
                    } else {
                        return context + identifier.substring(0, identifier.indexOf(")") - 1) + "($paramStr)"
                    }
                } else {
                    return context + identifier + "($paramStr)"
                }
            }
        }
    }

    @Override
    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false
        KytheNode that = (KytheNode) o
        if (signatureSalt != that.signatureSalt) return false
        return true
    }

    @Override
    int hashCode() {
        return (signatureSalt != null ? signatureSalt.hashCode() : 0)
    }
}
