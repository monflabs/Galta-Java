/*
 * Copyright (c) 2019-2026 Philippe Riand
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.monflabs.galtajs.node;

import java.util.Collections;
import java.util.List;

import org.monflabs.galtajs.node.assignop.ASTAssign;
import org.monflabs.galtajs.node.assignop.ASTAssignAdd;
import org.monflabs.galtajs.node.assignop.ASTAssignAnd;
import org.monflabs.galtajs.node.assignop.ASTAssignBitAnd;
import org.monflabs.galtajs.node.assignop.ASTAssignBitOr;
import org.monflabs.galtajs.node.assignop.ASTAssignBitXor;
import org.monflabs.galtajs.node.assignop.ASTAssignDiv;
import org.monflabs.galtajs.node.assignop.ASTAssignLShift;
import org.monflabs.galtajs.node.assignop.ASTAssignMod;
import org.monflabs.galtajs.node.assignop.ASTAssignMul;
import org.monflabs.galtajs.node.assignop.ASTAssignNullCoalescing;
import org.monflabs.galtajs.node.assignop.ASTAssignOr;
import org.monflabs.galtajs.node.assignop.ASTAssignPower;
import org.monflabs.galtajs.node.assignop.ASTAssignRShift;
import org.monflabs.galtajs.node.assignop.ASTAssignRunShift;
import org.monflabs.galtajs.node.assignop.ASTAssignSub;
import org.monflabs.galtajs.node.binaryop.ASTAdd;
import org.monflabs.galtajs.node.binaryop.ASTAnd;
import org.monflabs.galtajs.node.binaryop.ASTBitAnd;
import org.monflabs.galtajs.node.binaryop.ASTBitOr;
import org.monflabs.galtajs.node.binaryop.ASTBitXor;
import org.monflabs.galtajs.node.binaryop.ASTDiv;
import org.monflabs.galtajs.node.binaryop.ASTEq;
import org.monflabs.galtajs.node.binaryop.ASTEqStrict;
import org.monflabs.galtajs.node.binaryop.ASTGe;
import org.monflabs.galtajs.node.binaryop.ASTGt;
import org.monflabs.galtajs.node.binaryop.ASTIn;
import org.monflabs.galtajs.node.binaryop.ASTInstanceOf;
import org.monflabs.galtajs.node.binaryop.ASTLShift;
import org.monflabs.galtajs.node.binaryop.ASTLe;
import org.monflabs.galtajs.node.binaryop.ASTLt;
import org.monflabs.galtajs.node.binaryop.ASTMod;
import org.monflabs.galtajs.node.binaryop.ASTMul;
import org.monflabs.galtajs.node.binaryop.ASTNe;
import org.monflabs.galtajs.node.binaryop.ASTNeStrict;
import org.monflabs.galtajs.node.binaryop.ASTNullCoalescing;
import org.monflabs.galtajs.node.binaryop.ASTOr;
import org.monflabs.galtajs.node.binaryop.ASTPower;
import org.monflabs.galtajs.node.binaryop.ASTRShift;
import org.monflabs.galtajs.node.binaryop.ASTRunShift;
import org.monflabs.galtajs.node.binaryop.ASTSub;
import org.monflabs.galtajs.node.call.ASTCall;
import org.monflabs.galtajs.node.call.ASTCallSpread;
import org.monflabs.galtajs.node.call.ASTPipelineCall;
import org.monflabs.galtajs.node.clazz.ASTClassDecl;
import org.monflabs.galtajs.node.clazz.ASTClassField;
import org.monflabs.galtajs.node.clazz.ASTClassGetter;
import org.monflabs.galtajs.node.clazz.ASTClassMethod;
import org.monflabs.galtajs.node.clazz.ASTClassSetter;
import org.monflabs.galtajs.node.clazz.ASTClassStaticBlock;
import org.monflabs.galtajs.node.control.ASTBlock;
import org.monflabs.galtajs.node.control.ASTBreak;
import org.monflabs.galtajs.node.control.ASTCase;
import org.monflabs.galtajs.node.control.ASTCatch;
import org.monflabs.galtajs.node.control.ASTComment;
import org.monflabs.galtajs.node.control.ASTContinue;
import org.monflabs.galtajs.node.control.ASTDoWhile;
import org.monflabs.galtajs.node.control.ASTEmpty;
import org.monflabs.galtajs.node.control.ASTExport;
import org.monflabs.galtajs.node.control.ASTFor;
import org.monflabs.galtajs.node.control.ASTForIn;
import org.monflabs.galtajs.node.control.ASTForOf;
import org.monflabs.galtajs.node.control.ASTFunctionArrow;
import org.monflabs.galtajs.node.control.ASTFunctionDecl;
import org.monflabs.galtajs.node.control.ASTFunctionMethod;
import org.monflabs.galtajs.node.control.ASTIf;
import org.monflabs.galtajs.node.control.ASTImport;
import org.monflabs.galtajs.node.control.ASTReturn;
import org.monflabs.galtajs.node.control.ASTSwitch;
import org.monflabs.galtajs.node.control.ASTSynchronized;
import org.monflabs.galtajs.node.control.ASTThrow;
import org.monflabs.galtajs.node.control.ASTTry;
import org.monflabs.galtajs.node.control.ASTWhile;
import org.monflabs.galtajs.node.control.ASTWith;
import org.monflabs.galtajs.node.control.ASTYield;
import org.monflabs.galtajs.node.control.ASTYieldStar;
import org.monflabs.galtajs.node.debug.ASTDebugger;
import org.monflabs.galtajs.node.debug.DebuggableNode;
import org.monflabs.galtajs.node.debug.ASTDebugHook;
import org.monflabs.galtajs.node.literal.ASTArrayLiteral;
import org.monflabs.galtajs.node.literal.ASTLiteral;
import org.monflabs.galtajs.node.literal.ASTPrivateNameLiteral;
import org.monflabs.galtajs.node.literal.ASTObjectLiteral;
import org.monflabs.galtajs.node.literal.ASTRegExpLiteral;
import org.monflabs.galtajs.node.literal.ASTStringTemplate;
import org.monflabs.galtajs.node.ternaryop.ASTTernaryTest;
import org.monflabs.galtajs.node.unaryop.ASTAwait;
import org.monflabs.galtajs.node.unaryop.ASTBitNot;
import org.monflabs.galtajs.node.unaryop.ASTDelete;
import org.monflabs.galtajs.node.unaryop.ASTMinus;
import org.monflabs.galtajs.node.unaryop.ASTNewArray;
import org.monflabs.galtajs.node.unaryop.ASTNewObject;
import org.monflabs.galtajs.node.unaryop.ASTNot;
import org.monflabs.galtajs.node.unaryop.ASTParen;
import org.monflabs.galtajs.node.unaryop.ASTPlus;
import org.monflabs.galtajs.node.unaryop.ASTPostDec;
import org.monflabs.galtajs.node.unaryop.ASTPostInc;
import org.monflabs.galtajs.node.unaryop.ASTPreDec;
import org.monflabs.galtajs.node.unaryop.ASTPreInc;
import org.monflabs.galtajs.node.unaryop.ASTTypeof;
import org.monflabs.galtajs.node.unaryop.ASTVoid;
import org.monflabs.galtajs.node.variable.ASTVariableDeclConst;
import org.monflabs.galtajs.node.variable.ASTVariableDeclLocalVar;
import org.monflabs.galtajs.node.variable.ASTVariableDeclScopedLet;
import org.monflabs.galtajs.node.variable.ASTVariableDeclUsing;
import org.monflabs.galtajs.parser.Token;
import org.monflabs.galtajs.rt.RuntimeUtil;


/**
 * Factory used to create the expression nodes.
 */
public final class NodeFactory {
	
	public static final NodeFactory DEFAULT = new NodeFactory();
	
	protected NodeFactory() {
	}

	public ASTProgram createProgram(String text, List<ASTNode> nodes, boolean debugInfo) {
		return new ASTProgram(text, nodes, debugInfo);
	}

	public ASTNode createDebugHook(ASTNode node) {
		if(node==null) {
			return null;
		}
		if(node instanceof ASTDebugHook) {
			return node;
		}
		if(node instanceof DebuggableNode) {
			return node;
		}
		return new ASTDebugHook(node);
	}

	// Same as createDebugHook() above, but additionally marks the result as
	// a genuine statement-boundary wrap (see ASTDebugHook.isStatementLevel()'s
	// own doc) - used at Statement()'s own trailing wrap and the
	// MainSourceElements()/SourceElements() alternatives that bypass it, never
	// at a sub-expression site (a call argument, an if/while/for test/update).
	//
	// Deliberately does NOT delegate to createDebugHook() (and so does NOT
	// honor the DebuggableNode marker for ASTFor/ASTWhile/ASTDoWhile/
	// ASTIf): that keeps ScopeResolutionOptimizer.
	// enter()/UnreachableCodeRemovalOptimizer's own `instanceof ASTFor/
	// ASTWhile/...` checks from silently missing a wrapped node (confirmed no
	// test/production path currently combines debug=true with a configured
	// ScriptOptimizer, so this is a latent constraint, not a live bug here).
	// CDP-style stepping wants the opposite for these four: a `for(...)`/
	// `while(...)`/`if(...)`'s own header line must be a real, independent
	// statement-level pause point - see ASTDebugHook.isStatementLevel()'s
	// own doc and DebuggerImpl.onStatement()'s gate on it - or the header
	// line never gets a pause point at all (every child inside it is either
	// unwrapped, per for-loop init's own rationale two call sites up, or
	// wrapped but sub-expression-level). Confirmed via instrumentation:
	// without this, a stepOver() sitting right before a `for` loop skipped
	// straight into the loop body, silently jumping over the loop's own line.
	//
	// ASTBlock is the one exception kept excluded, same as before: a bare
	// block's own opening "{" is not a meaningful step target on its own
	// (V8/Node never pause there, only on the statements inside it) - wrapping
	// it would just add a spurious extra stop at the "{" itself (confirmed
	// via instrumentation too: it showed up as its own statement-level hit,
	// separate from the first real statement inside).
	public ASTNode createDebugHookStatement(ASTNode node) {
		if(node==null) {
			return null;
		}
		if(node instanceof ASTDebugHook aih) {
			aih.markStatementLevel();
			return aih;
		}
		if(node instanceof ASTBlock) {
			return node;
		}
		ASTDebugHook wrapped = new ASTDebugHook(node);
		wrapped.markStatementLevel();
		return wrapped;
	}

	public ASTLiteral createLiteral(Token t, Object value) {
		return new ASTLiteral(t, value);
	}

	public ASTPrivateNameLiteral createPrivateNameLiteral(Token t, String name) {
		return new ASTPrivateNameLiteral(t, name);
	}
	
	public ASTRegExpLiteral createRegExpLiteral(Token t, String regexp) {
		return new ASTRegExpLiteral(t, regexp);
	}
	
	public ASTStringTemplate createStringTemplate(Token t1, Token t2, ASTNode tagFunction, List<Object> parts) {
		return new ASTStringTemplate(t1,t2,tagFunction,parts);
	}

	public ASTEmpty createEmpty(Token t) {
		return new ASTEmpty(t);
	}
	
	public ASTObjectLiteral createObjectLiteral(Token t) {
		return new ASTObjectLiteral(t);
	}
	
	public ASTArrayLiteral createArrayLiteral(Token t) {
		return new ASTArrayLiteral(t);
	}
	
	public ASTThis createThis(Token t) {
		return new ASTThis(t);
	}

	public ASTClassDecl createClassDecl(Token t, String className, ASTNode superClass, List<ASTNode> elements) {
		return createClassDecl(t,className,superClass,elements,Collections.emptyList());
	}
	public ASTClassDecl createClassDecl(Token t, String className, ASTNode superClass, List<ASTNode> elements, List<ASTNode> decorators) {
		return new ASTClassDecl(t,className, superClass, elements, decorators);
	}
	public ASTClassField createClassField(Token t, Object name, ASTNode body, boolean isStatic, boolean isPrivate) {
		return createClassField(t,name,body,isStatic,isPrivate,Collections.emptyList());
	}
	public ASTClassField createClassField(Token t, Object name, ASTNode body, boolean isStatic, boolean isPrivate, List<ASTNode> decorators) {
		return new ASTClassField(t,name,body,isStatic,isPrivate,decorators);
	}
	public ASTClassField createClassField(Token t, Object name, ASTNode body, boolean isStatic, boolean isPrivate, List<ASTNode> decorators, boolean isAccessor) {
		return new ASTClassField(t,name,body,isStatic,isPrivate,decorators,isAccessor);
	}
	public ASTClassMethod createClassMethod(Token t, Object name, ASTFunctionMethod function, boolean isStatic, boolean isPrivate, boolean autoGenerated) {
		return createClassMethod(t,name,function,isStatic,isPrivate,autoGenerated,Collections.emptyList());
	}
	public ASTClassMethod createClassMethod(Token t, Object name, ASTFunctionMethod function, boolean isStatic, boolean isPrivate, boolean autoGenerated, List<ASTNode> decorators) {
		return new ASTClassMethod(t,name,function,isStatic,isPrivate,autoGenerated,decorators);
	}
	public ASTClassGetter createClassGetter(Token t, Object name, ASTFunctionMethod function, boolean isStatic, boolean isPrivate) {
		return createClassGetter(t,name,function,isStatic,isPrivate,Collections.emptyList());
	}
	public ASTClassGetter createClassGetter(Token t, Object name, ASTFunctionMethod function, boolean isStatic, boolean isPrivate, List<ASTNode> decorators) {
		return new ASTClassGetter(t,name,function,isStatic,isPrivate,decorators);
	}
	public ASTClassSetter createClassSetter(Token t, Object name, ASTFunctionMethod function, boolean isStatic, boolean isPrivate) {
		return createClassSetter(t,name,function,isStatic,isPrivate,Collections.emptyList());
	}
	public ASTClassSetter createClassSetter(Token t, Object name, ASTFunctionMethod function, boolean isStatic, boolean isPrivate, List<ASTNode> decorators) {
		return new ASTClassSetter(t,name,function,isStatic,isPrivate,decorators);
	}
	public ASTClassStaticBlock createClassStaticBlock(Token t, ASTBlock block) {
		return new ASTClassStaticBlock(t,block);
	}

	public ASTSuperCtor createSuperCtor(Token t, List<ASTNode> args) {
		return new ASTSuperCtor(t,args);
	}
	public ASTSuperMember createSuperMember(Token t) {
		return new ASTSuperMember(t);
	}

	public ASTImportCall createImportCall(Token t, ASTNode specifier) {
		return new ASTImportCall(t,specifier);
	}

	public ASTImportCall createImportCall(Token t, ASTNode specifier, ASTNode attributesNode, ASTImportCall.ImportCallMode mode) {
		return new ASTImportCall(t,specifier,attributesNode,mode);
	}

	public ASTImportMeta createImportMeta(Token t) {
		return new ASTImportMeta(t);
	}

	public ASTImport createImport(Token t) {
		return new ASTImport(t);
	}

	public ASTExport createExport(Token t) {
		return new ASTExport(t);
	}
	
	public ASTCall createFunctionCall(Token t, ASTNode node, List<ASTNode> args, boolean nullop) {
		return new ASTCall(t,node,args,nullop);
	}
	
	public ASTCallSpread createFunctionCallSpread(Token t, ASTNode node) {
		return new ASTCallSpread(t,node);
	}
	
	public ASTIdentifier createIdentifier(Token t, String id) {
		return new ASTIdentifier(t,id);
	}
	
	public ASTIdentifierFilter createIdentifierFilter(Token t) {
		return new ASTIdentifierFilter(t);
	}
	
	public ASTNewArray createNewArray(Token t, ASTNode type, int dimensions, ASTNode arrayExpression) {
		return new ASTNewArray(t, type, dimensions, arrayExpression);
	}
	
	public ASTNewObject createNewObject(Token t, ASTNode type, List<ASTNode> args) {
		return new ASTNewObject(t, type, args);
	}
	
	public ASTArrayMember createArrayMember(Token t, ASTNode node, boolean deepscan, boolean nullop) {
		return new ASTArrayMember(t, node, deepscan, nullop);
	}
	
	public ASTArrayMemberArray createArrayMemberArray(Token t, ASTNode node) {
		return new ASTArrayMemberArray(t, node);
	}
	
	public ASTMember createMember(Token t, ASTNode node, String memberName, boolean deepscan, boolean nullop) {
		return new ASTMember(t, node, memberName, deepscan, nullop);
	}
	
	public ASTNewMember createNewMember(Token t, String memberName) {
		return new ASTNewMember(t,memberName);
	}
	
	public ASTMemberSequence createMemberSequence(Token t, ASTNode node) {
		return new ASTMemberSequence(t, node);
	}
	
	public ASTArrayMemberFilter createMemberFilter(Token t, ASTNode node, ASTNode filter) {
		return new ASTArrayMemberFilter(t, node, filter);
	}
	
	public ASTArrayMemberMap createMemberMap(Token t, ASTNode node, ASTNode filter) {
		return new ASTArrayMemberMap(t, node, filter);
	}
	
	public ASTArrayMemberDeepScan createArrayMemberDeepScan(Token t, ASTNode node) {
		return new ASTArrayMemberDeepScan(t, node);
	}
	
	public ASTArrayMemberFlatten createArrayMemberFlatten(Token t, ASTNode node, boolean deepscan) {
		return new ASTArrayMemberFlatten(t, node, deepscan);
	}
	
	public ASTAwait createAwait(Token t, ASTNode node) {
		return new ASTAwait(t,node);
	}

	public ASTDelete createDelete(Token t, ASTNode node) {
		return new ASTDelete(t,node);
	}
	
	public ASTVoid createVoid(Token t, ASTNode node) {
		return new ASTVoid(t,node);
	}
	
	public ASTTypeof createTypeof(Token t, ASTNode node) {
		return new ASTTypeof(t,node);
	}
	
	public ASTPostInc createPostInc(Token t, ASTNode node) {
		return new ASTPostInc(t,node);
	}
	
	public ASTPostDec createPostDec(Token t, ASTNode node) {
		return new ASTPostDec(t,node);
	}
	
	public ASTPreInc createPreInc(Token t, ASTNode node) {
		return new ASTPreInc(t,node);
	}
	
	public ASTPreDec createPreDec(Token t, ASTNode node) {
		return new ASTPreDec(t,node);
	}
	
	public ASTPlus createPlus(Token t, ASTNode node) {
		return new ASTPlus(t, node);
	}
	
	public ASTMinus createMinus(Token t, ASTNode node) {
		return new ASTMinus(t, node);
	}
	
	public ASTBitNot createBitNot(Token t, ASTNode node) {
		return new ASTBitNot(t,node);
	}
	
	public ASTNot createNot(Token t, ASTNode node) {
		return new ASTNot(t,node);
	}
	
	public ASTParen createParen(Token t, ASTNode node) {
		return new ASTParen(t,node);
	}
	
	public ASTMul createMul(Token t, ASTNode leftNode, ASTNode rightNode) {
		return new ASTMul(t, leftNode, rightNode);
	}
	
	public ASTPower createPower(Token t, ASTNode leftNode, ASTNode rightNode) {
		return new ASTPower(t, leftNode, rightNode);
	}
	
	public ASTDiv createDiv(Token t, ASTNode leftNode, ASTNode rightNode) {
		return new ASTDiv(t, leftNode, rightNode);
	}
	
	public ASTMod createMod(Token t, ASTNode leftNode, ASTNode rightNode) {
		return new ASTMod(t, leftNode, rightNode);
	}
	
	public ASTAdd createAdd(Token t, ASTNode leftNode, ASTNode rightNode) {
		return new ASTAdd(t, leftNode, rightNode);
	}
	
	public ASTSub createSub(Token t, ASTNode leftNode, ASTNode rightNode) {
		return new ASTSub(t, leftNode, rightNode);
	}
	
	public ASTLt createLt(Token t, RuntimeUtil.MODE mode, ASTNode leftNode, ASTNode rightNode) {
		return new ASTLt(t, mode, leftNode, rightNode);
	}
	
	public ASTGt createGt(Token t, RuntimeUtil.MODE mode, ASTNode leftNode, ASTNode rightNode) {
		return new ASTGt(t, mode, leftNode, rightNode);
	}
	
	public ASTLe createLe(Token t, RuntimeUtil.MODE mode, ASTNode leftNode, ASTNode rightNode) {
		return new ASTLe(t, mode, leftNode, rightNode);
	}
	
	public ASTGe createGe(Token t, RuntimeUtil.MODE mode, ASTNode leftNode, ASTNode rightNode) {
		return new ASTGe(t, mode, leftNode, rightNode);
	}
	
	public ASTEq createEq(Token t, RuntimeUtil.MODE mode, ASTNode leftNode, ASTNode rightNode) {
		return new ASTEq(t, mode, leftNode, rightNode);
	}

	public ASTEqStrict createEqStrict(Token t, RuntimeUtil.MODE mode, ASTNode leftNode, ASTNode rightNode) {
		return new ASTEqStrict(t, mode, leftNode, rightNode);
	}
	
	public ASTNe createNe(Token t, RuntimeUtil.MODE mode, ASTNode leftNode, ASTNode rightNode) {
		return new ASTNe(t, mode, leftNode, rightNode);
	}
	
	public ASTNeStrict createNeStrict(Token t, RuntimeUtil.MODE mode, ASTNode leftNode, ASTNode rightNode) {
		return new ASTNeStrict(t, mode, leftNode, rightNode);
	}
	
	public ASTInstanceOf createInstanceOf(Token t, RuntimeUtil.MODE mode, ASTNode node, ASTNode rightNode) {
		return new ASTInstanceOf(t, mode, node, rightNode);
	}
	
	public ASTIn createIn(Token t, RuntimeUtil.MODE mode, ASTNode node, ASTNode rightNode) {
		return new ASTIn(t, mode, node, rightNode);
	}
	
	public ASTBitAnd createBitAnd(Token t, ASTNode leftNode, ASTNode rightNode) {
		return new ASTBitAnd(t, leftNode, rightNode);
	}
	
	public ASTBitXor createBitXor(Token t, ASTNode leftNode, ASTNode rightNode) {
		return new ASTBitXor(t, leftNode, rightNode);
	}
	
	public ASTBitOr createBitOr(Token t, ASTNode leftNode, ASTNode rightNode) {
		return new ASTBitOr(t, leftNode, rightNode);
	}
	
	public ASTAnd createAnd(Token t, ASTNode leftNode, ASTNode rightNode) {
		return new ASTAnd(t, leftNode, rightNode);
	}
	
	public ASTOr createOr(Token t, ASTNode leftNode, ASTNode rightNode) {
		return new ASTOr(t, leftNode, rightNode);
	}
	
	public ASTNullCoalescing createNullCoalescing (Token t, ASTNode leftNode, ASTNode rightNode) {
		return new ASTNullCoalescing(t, leftNode, rightNode);
	}
	
	public ASTLShift createLShift(Token t, ASTNode leftNode, ASTNode rightNode) {
		return new ASTLShift(t, leftNode, rightNode);
	}
	
	public ASTRShift createRShift(Token t, ASTNode leftNode, ASTNode rightNode) {
		return new ASTRShift(t, leftNode, rightNode);
	}
	
	public ASTRunShift createRunShift(Token t, ASTNode leftNode, ASTNode rightNode) {
		return new ASTRunShift(t, leftNode, rightNode);
	}
	
	public ASTTernaryTest createTernaryTest(Token t, ASTNode condNode, ASTNode thenNode, ASTNode elseNode) {
		return new ASTTernaryTest(t, condNode, thenNode, elseNode);
	}
	
	public ASTAssign createAssign(Token t, ASTNode leftNode, ASTNode rightNode) {
		return new ASTAssign(t, leftNode, rightNode);
	}
	
	public ASTAssignMul createAssignMul(Token t, ASTNode leftNode, ASTNode rightNode) {
		return new ASTAssignMul(t, leftNode, rightNode);
	}

	public ASTAssignPower createAssignPower(Token t, ASTNode leftNode, ASTNode rightNode) {
		return new ASTAssignPower(t, leftNode, rightNode);
	}
	
	public ASTAssignDiv createAssignDiv(Token t, ASTNode leftNode, ASTNode rightNode) {
		return new ASTAssignDiv(t, leftNode, rightNode);
	}
	
	public ASTAssignMod createAssignMod(Token t, ASTNode leftNode, ASTNode rightNode) {
		return new ASTAssignMod(t, leftNode, rightNode);
	}
	
	public ASTAssignAdd createAssignAdd(Token t, ASTNode leftNode, ASTNode rightNode) {
		return new ASTAssignAdd(t, leftNode, rightNode);
	}
	
	public ASTAssignSub createAssignSub(Token t, ASTNode leftNode, ASTNode rightNode) {
		return new ASTAssignSub(t, leftNode, rightNode);
	}
	
	public ASTAssignBitAnd createAssignBitAnd(Token t, ASTNode leftNode, ASTNode rightNode) {
		return new ASTAssignBitAnd(t, leftNode, rightNode);
	}
	
	public ASTAssignBitXor createAssignBitXor(Token t, ASTNode leftNode, ASTNode rightNode) {
		return new ASTAssignBitXor(t, leftNode, rightNode);
	}
	
	public ASTAssignBitOr createAssignBitOr(Token t, ASTNode leftNode, ASTNode rightNode) {
		return new ASTAssignBitOr(t, leftNode, rightNode);
	}
	
	public ASTAssignAnd createAssignAnd(Token t, ASTNode leftNode, ASTNode rightNode) {
		return new ASTAssignAnd(t, leftNode, rightNode);
	}
	
	public ASTAssignOr createAssignOr(Token t, ASTNode leftNode, ASTNode rightNode) {
		return new ASTAssignOr(t, leftNode, rightNode);
	}
	
	public ASTAssignLShift createAssignLShift(Token t, ASTNode leftNode, ASTNode rightNode) {
		return new ASTAssignLShift(t, leftNode, rightNode);
	}
	
	public ASTAssignRShift createAssignRShift(Token t, ASTNode leftNode, ASTNode rightNode) {
		return new ASTAssignRShift(t, leftNode, rightNode);
	}
	
	public ASTAssignRunShift createAssignRunShift(Token t, ASTNode leftNode, ASTNode rightNode) {
		return new ASTAssignRunShift(t, leftNode, rightNode);
	}
	
	public ASTPipelineCall createPipelineCall(Token t, ASTNode leftNode, ASTNode rightNode) {
		return new ASTPipelineCall(t, leftNode, rightNode);
	}
	
	public ASTAssignNullCoalescing createAssignNullCoalescing(Token t, ASTNode leftNode, ASTNode rightNode) {
		return new ASTAssignNullCoalescing(t, leftNode, rightNode);
	}
	
	public ASTFunctionDecl createFunctionDecl(Token t, String name, ASTArrayLiteral parameters, List<ASTNode> nodes) {
		return new ASTFunctionDecl(t,name,parameters,nodes);
	}
	public ASTFunctionArrow createFunctionArrow(Token t, ASTArrayLiteral parameters, List<ASTNode> nodes) {
		return new ASTFunctionArrow(t,parameters,nodes);
	}
	public ASTFunctionMethod createFunctionMethod(Token t, Object name, ASTArrayLiteral parameters, List<ASTNode> nodes) {
		String n = name instanceof CharSequence ? name.toString() : null;
		return new ASTFunctionMethod(t,n,parameters,nodes);
	}
	
	public ASTExpression createExpression(List<ASTNode> nodes) {
		return new ASTExpression(nodes);
	}
	
	public ASTBlock createBlock(Token t, List<ASTNode> statements) {
		return new ASTBlock(t,statements);
	}
	
	public ASTVariableDeclLocalVar createVariableDeclLocal(Token t) {
		return new ASTVariableDeclLocalVar(t);
	}
	
	public ASTVariableDeclScopedLet createVariableDeclScoped(Token t) {
		return new ASTVariableDeclScopedLet(t);
	}
	
	public ASTVariableDeclConst createVariableDeclConst(Token t) {
		return new ASTVariableDeclConst(t);
	}

	public ASTVariableDeclUsing createVariableDeclUsing(Token t, boolean isAwait) {
		return new ASTVariableDeclUsing(t, isAwait);
	}

	public ASTIf createIf(Token t, ASTNode testNode, ASTNode thenNode, ASTNode elseNode) {
		return new ASTIf(t, testNode, thenNode, elseNode);
	}
	
	public ASTDoWhile createDoWhile(Token t, ASTNode testNode, ASTNode bodyNode) {
		return new ASTDoWhile(t, testNode, bodyNode);
	}
	
	public ASTWhile createWhile(Token t, ASTNode testNode, ASTNode bodyNode) {
		return new ASTWhile(t, testNode, bodyNode);
	}
	
	public ASTFor createFor(Token t, ASTNode initNode, ASTNode testNode, ASTNode incNode, ASTNode bodyNode) {
		return new ASTFor(t, initNode, testNode, incNode, bodyNode);
	}
	
	public ASTForOf createForOf(Token t, ASTNode varDecl, ASTNode collectionNode, ASTNode bodyNode) {
		return new ASTForOf(t, varDecl, collectionNode, bodyNode);
	}

	public ASTForOf createForOf(Token t, ASTNode varDecl, ASTNode collectionNode, ASTNode bodyNode, boolean isAwait) {
		return new ASTForOf(t, varDecl, collectionNode, bodyNode, isAwait);
	}

	public ASTForIn createForIn(Token t, ASTNode varDecl, ASTNode collectionNode, ASTNode bodyNode) {
		return new ASTForIn(t, varDecl, collectionNode, bodyNode);
	}
	
	public ASTContinue createContinue(Token t, String label) {
		return new ASTContinue(t, label);
	}
	
	public ASTBreak createBreak(Token t, String label) {
		return new ASTBreak(t, label);
	}
	
	public ASTReturn createReturn(Token t, ASTNode node) {
		return new ASTReturn(t, node);
	}
	
	public ASTYield createYield(Token t, ASTNode node) {
		return new ASTYield(t, node);
	}
	
	public ASTYieldStar createYieldStar(Token t, ASTNode node) {
		return new ASTYieldStar(t, node);
	}
	
	public ASTSwitch createSwitch(Token t, ASTNode exprNode, List<ASTNode> cases) {
		return new ASTSwitch(t, exprNode, cases);
	}
	
	public ASTWith createWith(Token t, ASTNode exprNode, ASTNode statement) {
		return new ASTWith(t, exprNode, statement);
	}
	
	public ASTCase createCase(Token t, ASTNode exprNode, List<ASTNode> statements) {
		return new ASTCase(t, exprNode, statements);
	}
	
	public ASTTry createTry(Token t, ASTBlock bodyNode, ASTCatch catchNode, ASTBlock finallyNode) {
		return new ASTTry(t, bodyNode, catchNode, finallyNode);
	}
	
	public ASTThrow createThrow(Token t, ASTNode throwNode) {
		return new ASTThrow(t, throwNode);
	}
	
	public ASTCatch createCatch(Token t, ASTNode bindingNode, ASTBlock bodyNode) {
		return new ASTCatch(t, bindingNode, bodyNode);
	}
	
	public ASTDebugger createDebugger(Token t) {
		return new ASTDebugger(t);
	}
	
	public ASTSynchronized createSynchronized(Token t, ASTNode syncNode, ASTNode bodyNode) {
		return new ASTSynchronized(t, syncNode, bodyNode);
	}

	public ASTComment createComment(Token t, boolean singleLine, String comment) {
		return new ASTComment(t, singleLine, comment);
	}
}
