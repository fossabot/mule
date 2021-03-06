/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.context.notification;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.mule.runtime.core.DefaultEventContext.create;
import static org.mule.runtime.core.api.context.notification.EnrichedNotificationInfo.createInfo;
import static org.mule.runtime.core.api.context.notification.MessageProcessorNotification.MESSAGE_PROCESSOR_PRE_INVOKE;
import static org.mule.runtime.core.api.context.notification.PipelineMessageNotification.PROCESS_START;
import static org.mule.runtime.core.internal.context.notification.MessageProcessingFlowTraceManager.FLOW_STACK_INFO_KEY;

import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.meta.AnnotatedObject;
import org.mule.runtime.core.api.InternalEvent;
import org.mule.runtime.core.api.InternalEventContext;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.config.DefaultMuleConfiguration;
import org.mule.runtime.core.api.config.MuleConfiguration;
import org.mule.runtime.core.api.construct.FlowConstruct;
import org.mule.runtime.core.api.construct.Pipeline;
import org.mule.runtime.core.api.context.notification.FlowCallStack;
import org.mule.runtime.core.api.context.notification.MessageProcessorNotification;
import org.mule.runtime.core.api.context.notification.PipelineMessageNotification;
import org.mule.runtime.core.api.context.notification.ProcessorsTrace;
import org.mule.runtime.core.api.processor.Processor;
import org.mule.tck.junit4.AbstractMuleTestCase;
import org.mule.tck.size.SmallTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.xml.namespace.QName;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

@SmallTest
public class MessageProcessingFlowTraceManagerTestCase extends AbstractMuleTestCase {

  private static QName docNameAttrName = new QName("http://www.mulesoft.org/schema/mule/documentation", "name");
  private static QName sourceFileNameAttrName = new QName("http://www.mulesoft.org/schema/mule/documentation", "sourceFileName");
  private static QName sourceFileLineAttrName = new QName("http://www.mulesoft.org/schema/mule/documentation", "sourceFileLine");

  private static final String NESTED_FLOW_NAME = "nestedFlow";
  private static final String ROOT_FLOW_NAME = "rootFlow";
  private static final String APP_ID = "MessageProcessingFlowTraceManagerTestCase";

  private static boolean originalFlowTrace;

  @BeforeClass
  public static void beforeClass() {
    originalFlowTrace = DefaultMuleConfiguration.flowTrace;
    DefaultMuleConfiguration.flowTrace = true;
  }

  @AfterClass
  public static void afterClass() {
    DefaultMuleConfiguration.flowTrace = originalFlowTrace;
  }

  private MessageProcessingFlowTraceManager manager;
  private InternalEventContext messageContext;

  private FlowConstruct rootFlowConstruct;
  private FlowConstruct nestedFlowConstruct;

  @Before
  public void before() {
    manager = new MessageProcessingFlowTraceManager();
    MuleContext context = mock(MuleContext.class);
    MuleConfiguration config = mock(MuleConfiguration.class);
    when(config.getId()).thenReturn(APP_ID);
    when(context.getConfiguration()).thenReturn(config);
    manager.setMuleContext(context);

    rootFlowConstruct = mock(FlowConstruct.class);
    when(rootFlowConstruct.getName()).thenReturn(ROOT_FLOW_NAME);
    when(rootFlowConstruct.getMuleContext()).thenReturn(context);
    nestedFlowConstruct = mock(FlowConstruct.class);
    when(nestedFlowConstruct.getName()).thenReturn(NESTED_FLOW_NAME);
    when(nestedFlowConstruct.getMuleContext()).thenReturn(context);

    messageContext = create(rootFlowConstruct, TEST_CONNECTOR_LOCATION);
  }

  @Test
  public void newFlowInvocation() {
    InternalEvent event = buildEvent("newFlowInvocation");
    PipelineMessageNotification pipelineNotification = buildPipelineNotification(event, rootFlowConstruct.getName());
    assertThat(getContextInfo(event, rootFlowConstruct), is(""));

    manager.onPipelineNotificationStart(pipelineNotification);
    assertThat(getContextInfo(event, rootFlowConstruct), is("at " + rootFlowConstruct.getName()));

    manager.onPipelineNotificationComplete(pipelineNotification);
    assertThat(getContextInfo(event, rootFlowConstruct), is(""));
  }

  @Test
  public void nestedFlowInvocations() {
    InternalEvent event = buildEvent("nestedFlowInvocations");
    PipelineMessageNotification pipelineNotification = buildPipelineNotification(event, rootFlowConstruct.getName());
    assertThat(getContextInfo(event, rootFlowConstruct), is(""));

    manager.onPipelineNotificationStart(pipelineNotification);
    manager.onMessageProcessorNotificationPreInvoke(buildProcessorNotification(event,
                                                                               createMockProcessor(NESTED_FLOW_NAME + "_ref")));

    PipelineMessageNotification pipelineNotificationNested = buildPipelineNotification(event, NESTED_FLOW_NAME);
    manager.onPipelineNotificationStart(pipelineNotificationNested);

    String rootEntry = "at " + rootFlowConstruct.getName() + "(" + NESTED_FLOW_NAME + "_ref @ " + APP_ID + ":null:null)";
    assertThat(getContextInfo(event, rootFlowConstruct), is("at " + NESTED_FLOW_NAME + System.lineSeparator() + rootEntry));

    manager.onPipelineNotificationComplete(pipelineNotificationNested);
    assertThat(getContextInfo(event, rootFlowConstruct), is(rootEntry));

    manager.onPipelineNotificationComplete(pipelineNotification);
    assertThat(getContextInfo(event, rootFlowConstruct), is(""));
  }

  @Test
  public void newComponentCall() {
    InternalEvent event = buildEvent("newComponentCall");
    PipelineMessageNotification pipelineNotification = buildPipelineNotification(event, rootFlowConstruct.getName());
    assertThat(getContextInfo(event, rootFlowConstruct), is(""));

    manager.onPipelineNotificationStart(pipelineNotification);
    assertThat(getContextInfo(event, rootFlowConstruct), is("at " + ROOT_FLOW_NAME));

    manager.onMessageProcessorNotificationPreInvoke(buildProcessorNotification(event, createMockProcessor("/comp")));
    assertThat(getContextInfo(event, rootFlowConstruct), is("at " + ROOT_FLOW_NAME + "(/comp @ " + APP_ID + ":null:null)"));

    manager.onPipelineNotificationComplete(pipelineNotification);
    assertThat(getContextInfo(event, rootFlowConstruct), is(""));
  }

  protected String getContextInfo(InternalEvent event, FlowConstruct flow) {
    return (String) manager.getContextInfo(createInfo(event, null, null), null).get(FLOW_STACK_INFO_KEY);
  }

  @Test
  public void newAnnotatedComponentCall() {
    InternalEvent event = buildEvent("newAnnotatedComponentCall");
    PipelineMessageNotification pipelineNotification = buildPipelineNotification(event, rootFlowConstruct.getName());
    assertThat(getContextInfo(event, rootFlowConstruct), is(""));

    manager.onPipelineNotificationStart(pipelineNotification);
    assertThat(getContextInfo(event, rootFlowConstruct), is("at " + rootFlowConstruct.getName()));

    AnnotatedObject annotatedMessageProcessor = (AnnotatedObject) createMockProcessor("/comp");

    when(annotatedMessageProcessor.getAnnotation(docNameAttrName)).thenReturn("annotatedName");
    when(annotatedMessageProcessor.getAnnotation(sourceFileNameAttrName)).thenReturn("muleApp.xml");
    when(annotatedMessageProcessor.getAnnotation(sourceFileLineAttrName)).thenReturn(10);
    manager
        .onMessageProcessorNotificationPreInvoke(buildProcessorNotification(event, (Processor) annotatedMessageProcessor));
    assertThat(getContextInfo(event, rootFlowConstruct),
               is("at " + rootFlowConstruct.getName() + "(/comp @ " + APP_ID + ":muleApp.xml:10 (annotatedName))"));

    manager.onPipelineNotificationComplete(pipelineNotification);
    assertThat(getContextInfo(event, rootFlowConstruct), is(""));
  }

  @Test
  public void splitStack() {
    InternalEvent event = buildEvent("newAnnotatedComponentCall");
    PipelineMessageNotification pipelineNotification = buildPipelineNotification(event, rootFlowConstruct.getName());
    assertThat(getContextInfo(event, rootFlowConstruct), is(""));

    manager.onPipelineNotificationStart(pipelineNotification);
    assertThat(getContextInfo(event, rootFlowConstruct), is("at " + rootFlowConstruct.getName()));

    manager.onMessageProcessorNotificationPreInvoke(buildProcessorNotification(event, createMockProcessor("/comp")));
    assertThat(getContextInfo(event, rootFlowConstruct),
               is("at " + rootFlowConstruct.getName() + "(/comp @ " + APP_ID + ":null:null)"));

    InternalEvent eventCopy = buildEvent("newAnnotatedComponentCall", event.getFlowCallStack().clone());
    assertThat(getContextInfo(eventCopy, rootFlowConstruct),
               is("at " + rootFlowConstruct.getName() + "(/comp @ " + APP_ID + ":null:null)"));

    manager.onPipelineNotificationComplete(pipelineNotification);
    assertThat(getContextInfo(event, rootFlowConstruct), is(""));

    final FlowConstruct asyncFlowConstruct = mock(FlowConstruct.class);
    when(asyncFlowConstruct.getName()).thenReturn("asyncFlow");
    manager.onPipelineNotificationStart(buildPipelineNotification(eventCopy, asyncFlowConstruct.getName()));

    manager.onMessageProcessorNotificationPreInvoke(buildProcessorNotification(eventCopy, createMockProcessor("/asyncComp")));
    assertThat(getContextInfo(eventCopy, asyncFlowConstruct),
               is("at " + asyncFlowConstruct.getName() + "(/asyncComp @ " + APP_ID + ":null:null)" + System.lineSeparator()
                   + "at " + rootFlowConstruct.getName() + "(/comp @ " + APP_ID + ":null:null)"));
  }

  @Test
  public void splitStackEnd() {
    InternalEvent event = buildEvent("newAnnotatedComponentCall");
    PipelineMessageNotification pipelineNotification = buildPipelineNotification(event, rootFlowConstruct.getName());

    manager.onPipelineNotificationStart(pipelineNotification);
    manager.onMessageProcessorNotificationPreInvoke(buildProcessorNotification(event, createMockProcessor("/comp")));
    FlowCallStack flowCallStack = event.getFlowCallStack();
    InternalEvent eventCopy = buildEvent("newAnnotatedComponentCall", flowCallStack.clone());
    manager.onPipelineNotificationComplete(pipelineNotification);
    String asyncFlowName = "asyncFlow";
    manager.onPipelineNotificationStart(buildPipelineNotification(eventCopy, asyncFlowName));
    manager.onMessageProcessorNotificationPreInvoke(buildProcessorNotification(eventCopy, createMockProcessor("/asyncComp")));

    assertThat(event.getContext().getProcessorsTrace(),
               hasExecutedProcessors("/comp @ " + APP_ID + ":null:null", "/asyncComp @ " + APP_ID + ":null:null"));
  }

  @Test
  public void joinStack() {
    InternalEvent event = buildEvent("joinStack");
    PipelineMessageNotification pipelineNotification = buildPipelineNotification(event, rootFlowConstruct.getName());
    assertThat(getContextInfo(event, rootFlowConstruct), is(""));

    manager.onPipelineNotificationStart(pipelineNotification);
    assertThat(getContextInfo(event, rootFlowConstruct), is("at " + rootFlowConstruct.getName()));

    manager.onMessageProcessorNotificationPreInvoke(buildProcessorNotification(event, createMockProcessor("/scatter-gather")));
    assertThat(getContextInfo(event, rootFlowConstruct),
               is("at " + rootFlowConstruct.getName() + "(/scatter-gather @ " + APP_ID + ":null:null)"));

    InternalEvent eventCopy0 = buildEvent("joinStack_0", event.getFlowCallStack().clone());
    InternalEvent eventCopy1 = buildEvent("joinStack_1", event.getFlowCallStack().clone());

    manager.onMessageProcessorNotificationPreInvoke(buildProcessorNotification(eventCopy0, createMockProcessor("/route_0")));

    manager.onMessageProcessorNotificationPreInvoke(buildProcessorNotification(eventCopy1,
                                                                               createMockProcessor(NESTED_FLOW_NAME + "_ref")));
    PipelineMessageNotification pipelineNotificationNested = buildPipelineNotification(eventCopy1, NESTED_FLOW_NAME);
    manager.onPipelineNotificationStart(pipelineNotificationNested);
    manager.onMessageProcessorNotificationPreInvoke(buildProcessorNotification(eventCopy1, createMockProcessor("/route_1")));
    assertThat(getContextInfo(eventCopy1, rootFlowConstruct),
               is("at " + NESTED_FLOW_NAME + "(/route_1 @ " + APP_ID + ":null:null)" + System.lineSeparator()
                   + "at " + ROOT_FLOW_NAME + "(" + NESTED_FLOW_NAME + "_ref @ " + APP_ID + ":null:null)"));

    manager.onPipelineNotificationComplete(pipelineNotificationNested);

    assertThat(getContextInfo(event, rootFlowConstruct),
               is("at " + rootFlowConstruct.getName() + "(/scatter-gather @ " + APP_ID + ":null:null)"));

    manager.onPipelineNotificationComplete(pipelineNotification);
    assertThat(getContextInfo(event, rootFlowConstruct), is(""));
  }

  public Processor createMockProcessor(String processorPath) {
    ComponentLocation componentLocation = mock(ComponentLocation.class);
    when(componentLocation.getLocation()).thenReturn(processorPath);

    AnnotatedObject annotatedMessageProcessor =
        (AnnotatedObject) mock(Processor.class,
                               withSettings().extraInterfaces(AnnotatedObject.class).defaultAnswer(RETURNS_DEEP_STUBS));

    when(annotatedMessageProcessor.getAnnotation(any())).thenReturn(null);
    when(annotatedMessageProcessor.getLocation()).thenReturn(componentLocation);

    return (Processor) annotatedMessageProcessor;
  }

  @Test
  public void joinStackEnd() {
    InternalEvent event = buildEvent("joinStack");
    PipelineMessageNotification pipelineNotification = buildPipelineNotification(event, rootFlowConstruct.getName());

    manager.onPipelineNotificationStart(pipelineNotification);
    manager.onMessageProcessorNotificationPreInvoke(buildProcessorNotification(event, createMockProcessor("/scatter-gather")));

    FlowCallStack flowCallStack = event.getFlowCallStack();
    InternalEvent eventCopy0 = buildEvent("joinStack_0", flowCallStack.clone());
    InternalEvent eventCopy1 = buildEvent("joinStack_1", flowCallStack.clone());

    manager.onMessageProcessorNotificationPreInvoke(buildProcessorNotification(eventCopy0, createMockProcessor("/route_0")));

    manager.onMessageProcessorNotificationPreInvoke(buildProcessorNotification(eventCopy1,
                                                                               createMockProcessor(NESTED_FLOW_NAME + "_ref")));
    PipelineMessageNotification pipelineNotificationNested = buildPipelineNotification(eventCopy1, NESTED_FLOW_NAME);
    manager.onPipelineNotificationStart(pipelineNotificationNested);
    manager.onMessageProcessorNotificationPreInvoke(buildProcessorNotification(eventCopy1, createMockProcessor("/route_1")));
    manager.onPipelineNotificationComplete(pipelineNotificationNested);

    manager.onPipelineNotificationComplete(pipelineNotification);

    assertThat(event.getContext().getProcessorsTrace(),
               hasExecutedProcessors("/scatter-gather @ " + APP_ID + ":null:null", "/route_0 @ " + APP_ID + ":null:null",
                                     NESTED_FLOW_NAME + "_ref @ " + APP_ID + ":null:null",
                                     "/route_1 @ " + APP_ID + ":null:null"));
  }

  @Test
  public void mixedEvents() {
    InternalEvent event1 = buildEvent("mixedEvents_1");
    InternalEvent event2 = buildEvent("mixedEvents_2");

    PipelineMessageNotification pipelineNotification1 = buildPipelineNotification(event1, rootFlowConstruct.getName());
    PipelineMessageNotification pipelineNotification2 = buildPipelineNotification(event2, rootFlowConstruct.getName());
    assertThat(getContextInfo(event1, rootFlowConstruct), is(""));
    assertThat(getContextInfo(event2, rootFlowConstruct), is(""));

    manager.onPipelineNotificationStart(pipelineNotification1);
    assertThat(getContextInfo(event1, rootFlowConstruct), is("at " + rootFlowConstruct.getName()));
    assertThat(getContextInfo(event2, rootFlowConstruct), is(""));

    manager.onPipelineNotificationStart(pipelineNotification2);
    assertThat(getContextInfo(event1, rootFlowConstruct), is("at " + rootFlowConstruct.getName()));
    assertThat(getContextInfo(event2, rootFlowConstruct), is("at " + rootFlowConstruct.getName()));

    manager.onPipelineNotificationComplete(pipelineNotification1);
    assertThat(getContextInfo(event1, rootFlowConstruct), is(""));
    assertThat(getContextInfo(event2, rootFlowConstruct), is("at " + rootFlowConstruct.getName()));

    manager.onPipelineNotificationComplete(pipelineNotification2);
    assertThat(getContextInfo(event1, rootFlowConstruct), is(""));
    assertThat(getContextInfo(event2, rootFlowConstruct), is(""));
  }

  protected InternalEvent buildEvent(String eventId) {
    return buildEvent(eventId, new DefaultFlowCallStack());
  }

  protected InternalEvent buildEvent(String eventId, FlowCallStack flowStack) {
    InternalEvent event = mock(InternalEvent.class);
    when(event.getContext()).thenReturn(messageContext);
    when(event.getContext()).thenReturn(messageContext);
    when(event.getFlowCallStack()).thenReturn(flowStack);
    return event;
  }

  protected MessageProcessorNotification buildProcessorNotification(InternalEvent event, Processor processor) {
    return MessageProcessorNotification.createFrom(event, null, processor, null, MESSAGE_PROCESSOR_PRE_INVOKE);
  }

  protected PipelineMessageNotification buildPipelineNotification(InternalEvent event, String name) {
    Pipeline flowConstruct = mock(Pipeline.class, withSettings().extraInterfaces(AnnotatedObject.class));
    when(flowConstruct.getName()).thenReturn(name);

    return new PipelineMessageNotification(createInfo(event, null, null), flowConstruct, PROCESS_START);
  }

  private Matcher<ProcessorsTrace> hasExecutedProcessors(final String... expectedProcessors) {
    return new TypeSafeMatcher<ProcessorsTrace>() {

      private List<Matcher> failed = new ArrayList<>();

      @Override
      protected boolean matchesSafely(ProcessorsTrace processorsTrace) {
        Matcher<Collection<? extends Object>> sizeMatcher = hasSize(expectedProcessors.length);
        if (!sizeMatcher.matches(processorsTrace.getExecutedProcessors())) {
          failed.add(sizeMatcher);
        }

        int i = 0;
        for (String expectedProcessor : expectedProcessors) {
          Matcher processorItemMatcher = is(expectedProcessor);
          if (!processorItemMatcher.matches(processorsTrace.getExecutedProcessors().get(i))) {
            failed.add(processorItemMatcher);
          }
          ++i;
        }

        return failed.isEmpty();
      }

      @Override
      public void describeTo(Description description) {
        description.appendValue(Arrays.asList(expectedProcessors));
      }

      @Override
      protected void describeMismatchSafely(ProcessorsTrace item, Description description) {
        description.appendText("was ").appendValue(item.getExecutedProcessors());
      }
    };
  }

}
