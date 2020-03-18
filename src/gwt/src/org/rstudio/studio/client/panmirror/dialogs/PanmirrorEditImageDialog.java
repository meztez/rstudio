/*
 * PanmirrorEditImageDialog.java
 *
 * Copyright (C) 2009-20 by RStudio, PBC
 *
 * Unless you have received this program directly from RStudio pursuant
 * to the terms of a commercial license agreement with RStudio, then
 * this program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */


package org.rstudio.studio.client.panmirror.dialogs;

import org.rstudio.core.client.ElementIds;
import org.rstudio.core.client.StringUtil;
import org.rstudio.core.client.files.FileSystemItem;
import org.rstudio.core.client.theme.DialogTabLayoutPanel;
import org.rstudio.core.client.theme.VerticalTabPanel;
import org.rstudio.core.client.widget.FormLabel;
import org.rstudio.core.client.widget.ModalDialog;
import org.rstudio.core.client.widget.NumericTextBox;
import org.rstudio.core.client.widget.OperationWithInput;
import org.rstudio.studio.client.RStudioGinjector;
import org.rstudio.studio.client.common.GlobalDisplay;
import org.rstudio.studio.client.panmirror.dialogs.model.PanmirrorAttrProps;
import org.rstudio.studio.client.panmirror.dialogs.model.PanmirrorImageDimensions;
import org.rstudio.studio.client.panmirror.dialogs.model.PanmirrorImageProps;
import org.rstudio.studio.client.panmirror.uitools.PanmirrorUITools;
import org.rstudio.studio.client.panmirror.uitools.PanmirrorUIToolsImage;

import com.google.gwt.aria.client.Roles;
import com.google.gwt.dom.client.Document;
import com.google.gwt.event.dom.client.DomEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;


public class PanmirrorEditImageDialog extends ModalDialog<PanmirrorImageProps>
{
   public PanmirrorEditImageDialog(PanmirrorImageProps props,
                                   PanmirrorImageDimensions dims,
                                   String resourceDir,
                                   boolean editAttributes,
                                   OperationWithInput<PanmirrorImageProps> operation)
   {
      super("Image", Roles.getDialogRole(), operation, () -> {
         // cancel returns null
         operation.execute(null);
      }); 
      
      uiTools_ = new PanmirrorUITools().image;
      
      dims_ = dims;
      
      widthProp_ = props.width;
      heightProp_ = props.height;
      unitsProp_ = props.units;
      
      VerticalTabPanel imageTab = new VerticalTabPanel(ElementIds.VISUAL_MD_IMAGE_TAB_IMAGE);
      imageTab.addStyleName(RES.styles().dialog());
      
      
      imageTab.add(url_ = new PanmirrorImageChooser(FileSystemItem.createDir(resourceDir)));
      url_.addStyleName(RES.styles().spaced());
      if (!StringUtil.isNullOrEmpty(props.src))
         url_.setText(props.src);
      
      
      // size 
      HorizontalPanel sizePanel = new HorizontalPanel();
      sizePanel.addStyleName(RES.styles().spaced());
      sizePanel.setVerticalAlignment(HasVerticalAlignment.ALIGN_MIDDLE);
      
      width_ = addSizeInput(sizePanel, ElementIds.VISUAL_MD_IMAGE_WIDTH, "Width:");
      height_ = addSizeInput(sizePanel, ElementIds.VISUAL_MD_IMAGE_HEIGHT, "Height:");
      heightAuto_ = createHorizontalLabel("(Auto)");
      heightAuto_.addStyleName(RES.styles().heightAuto());
      sizePanel.add(heightAuto_);
      units_ = addUnitsSelect(sizePanel);
      initSizeInputs();
      
      lockRatio_ = new CheckBox("Lock ratio");
      lockRatio_.addStyleName(RES.styles().lockRatioCheckbox());
      lockRatio_.getElement().setId(ElementIds.VISUAL_MD_IMAGE_LOCK_RATIO);
      lockRatio_.setValue(props.lockRatio);
      sizePanel.add(lockRatio_);
      
      width_.addChangeHandler(event -> {
         String width = width_.getText();
         widthProp_ = StringUtil.isNullOrEmpty(width) ? null : Double.parseDouble(width);
         if (widthProp_ != null && lockRatio_.getValue()) {
            double height = widthProp_ * (dims_.naturalHeight/dims_.naturalWidth);
            height_.setValue(uiTools_.roundUnit(height, units_.getSelectedValue()));
            heightProp_ = Double.parseDouble(height_.getValue());
         }
         unitsProp_ = units_.getSelectedValue();
      });
      
      height_.addChangeHandler(event -> {
         String height = height_.getText();
         heightProp_ = StringUtil.isNullOrEmpty(height) ? null : Double.parseDouble(height);
         if (heightProp_ != null && lockRatio_.getValue()) {
            double width = heightProp_ * (dims_.naturalWidth/dims_.naturalHeight);
            width_.setValue(uiTools_.roundUnit(width, units_.getSelectedValue()));
            widthProp_ = Double.parseDouble(width_.getValue());
         }
         unitsProp_ = units_.getSelectedValue();
      });
      
      units_.addChangeHandler(event -> {
         
         String width = width_.getText();  
         if (!StringUtil.isNullOrEmpty(width)) 
         {
            double widthPixels = uiTools_.unitToPixels(Double.parseDouble(width), prevUnits_, dims_.containerWidth);
            double widthUnit = uiTools_.pixelsToUnit(widthPixels, units_.getSelectedValue(), dims_.containerWidth);
            width_.setText(uiTools_.roundUnit(widthUnit, units_.getSelectedValue()));
            widthProp_ = Double.parseDouble(width_.getValue());
         }
         
         String height = height_.getText();
         if (!StringUtil.isNullOrEmpty(height)) 
         {
            double heightPixels = uiTools_.unitToPixels(Double.parseDouble(height), prevUnits_, dims_.containerWidth);
            double heightUnit = uiTools_.pixelsToUnit(heightPixels, units_.getSelectedValue(), dims_.containerWidth);
            height_.setText(uiTools_.roundUnit(heightUnit, units_.getSelectedValue()));
            heightProp_ = Double.parseDouble(height_.getValue());
         }
         
         prevUnits_ = units_.getSelectedValue();
         
         unitsProp_ = units_.getSelectedValue();
         
         manageUnitsUI();
      });

      manageUnitsUI();
      
      // only add sizing controls if we support editAttributes and dims have been provided
      if (editAttributes && dims_ != null)
      {
         imageTab.add(sizePanel);
      }
      
      title_ = PanmirrorDialogsUtil.addTextBox(imageTab, ElementIds.VISUAL_MD_IMAGE_TITLE, "Title/Tooltip:", props.title);
      alt_ = PanmirrorDialogsUtil.addTextBox(imageTab, ElementIds.VISUAL_MD_IMAGE_ALT, "Caption/Alt:", props.alt); 
         
      editAttr_ =  new PanmirrorEditAttrWidget();
      editAttr_.setAttr(props);
      
      if (editAttributes)
      {
         VerticalTabPanel attributesTab = new VerticalTabPanel(ElementIds.VISUAL_MD_IMAGE_TAB_ATTRIBUTES);
         attributesTab.addStyleName(RES.styles().dialog());
         attributesTab.add(editAttr_);
         
         DialogTabLayoutPanel tabPanel = new DialogTabLayoutPanel("Image");
         tabPanel.addStyleName(RES.styles().imageDialogTabs());
         tabPanel.add(imageTab, "Image", imageTab.getBasePanelId());
         tabPanel.add(attributesTab, "Attributes", attributesTab.getBasePanelId());
         tabPanel.selectTab(0);
         
         mainWidget_ = tabPanel;
      }
      else
      {
         mainWidget_ = imageTab;
      }
   }
   
   @Override
   protected Widget createMainWidget()
   {
      return mainWidget_;
   }
   
   @Override
   public void focusFirstControl()
   {
      url_.getTextBox().setFocus(true);
      url_.getTextBox().setSelectionRange(0, 0);
   }
   
   @Override
   protected PanmirrorImageProps collectInput()
   {
      PanmirrorImageProps result = new PanmirrorImageProps();
      result.src = url_.getTextBox().getValue().trim();
      result.title = title_.getValue().trim();
      result.alt = alt_.getValue().trim();
      result.width = widthProp_;
      result.height = heightProp_;
      result.units = unitsProp_;
      result.lockRatio = lockRatio_.getValue();
      PanmirrorAttrProps attr = editAttr_.getAttr();
      result.id = attr.id;
      result.classes = attr.classes;
      result.keyvalue = attr.keyvalue;
      return result;
   }
   
   @Override
   protected boolean validate(PanmirrorImageProps result)
   {
      // width is required if height is specified
      if (height_.getText().trim().length() > 0)
      {
         GlobalDisplay globalDisplay = RStudioGinjector.INSTANCE.getGlobalDisplay();
         String width = width_.getText().trim();
         if (width.length() == 0)
         {
            globalDisplay.showErrorMessage(
               "Error", "You must provide a value for image width."
            );
            width_.setFocus(true);
            return false;
         }
         else
         {
            return true;
         }
      }
      else
      {
         return true;
      }
     
      
   }
   
   private void initSizeInputs()
   {
      // only init for existing images (i.e. dims passed)
      if (dims_ == null)
         return;
      
      String width = null, height = null, units = "px"; 
      
      // if we have both width and height then use them
      if (widthProp_ != null && heightProp_ != null)
      {
         width = widthProp_.toString();
         height = heightProp_.toString();
         units = unitsProp_;
      }
      
      // if there is no width or height, use pixels
      else if (widthProp_ == null && heightProp_ == null)
      {
         width = dims_.naturalWidth.toString();
         height = dims_.naturalHeight.toString();
         units = "px";
      }
      
      else if (dims_.naturalHeight != null && dims_.naturalWidth != null)
      {
         // if there is width only then show computed height
         units = unitsProp_;
         if (widthProp_ != null)
         {
            width = widthProp_.toString();
            height = uiTools_.roundUnit(widthProp_ * (dims_.naturalHeight/dims_.naturalWidth), units);
         }
         else if (heightProp_ != null)
         {
            height = heightProp_.toString();
            width = uiTools_.roundUnit(heightProp_ * (dims_.naturalWidth/dims_.naturalHeight), units);
         }
      }
      
      // set values into inputs
      width_.setValue(width);
      height_.setValue(height);
      for (int i = 0; i<units_.getItemCount(); i++)
      {
         if (units_.getItemText(i) == units)
         {
            units_.setSelectedIndex(i);
            prevUnits_ = units;
            break;
         }
      }
   }
   
   private void manageUnitsUI()
   {
      boolean percentUnits = units_.getSelectedValue() == uiTools_.percentUnit();
      
      if (percentUnits)
      {
         lockRatio_.setValue(true);
         lockRatio_.setEnabled(false);
      }
      else
      {
         lockRatio_.setEnabled(true);
      }
      
      height_.setVisible(!percentUnits);
      heightAuto_.setVisible(percentUnits);
   }

   

   private static NumericTextBox addSizeInput(Panel panel, String id, String labelText)
   {
      FormLabel label = createHorizontalLabel(labelText);
      NumericTextBox input = new NumericTextBox();
      input.getElement().addClassName(allowEnterKeyClass);
      input.addKeyUpHandler(event -> {
         int keycode = event.getNativeKeyCode();
         if (keycode == KeyCodes.KEY_ENTER) 
         {
            event.preventDefault();
            event.stopPropagation();
            DomEvent.fireNativeEvent(Document.get().createChangeEvent(), input);
         }
      });
      input.setMin(1);
      input.setMax(10000);
      input.addStyleName(RES.styles().horizontalInput());
      input.getElement().setId(id);
      label.setFor(input);
      panel.add(label);
      panel.add(input);
      return input;
   }
   
   private ListBox addUnitsSelect(Panel panel)
   {
      String[] options = uiTools_.validUnits();
      ListBox units = new ListBox();
      units.addStyleName(RES.styles().horizontalInput());
      for (int i = 0; i < options.length; i++)
         units.addItem(options[i], options[i]);
      units.getElement().setId(ElementIds.VISUAL_MD_IMAGE_UNITS);
      panel.add(units);
      return units;
   }
   
   private static FormLabel createHorizontalLabel(String text)
   {
      FormLabel label = new FormLabel(text);
      label.addStyleName(RES.styles().horizontalLabel());
      return label;
   }
   
   private static PanmirrorDialogsResources RES = PanmirrorDialogsResources.INSTANCE;
   
   private final PanmirrorImageDimensions dims_;
   
   private final Widget mainWidget_;
   
   private final PanmirrorUIToolsImage uiTools_;

   private Double widthProp_ = null;
   private Double heightProp_ = null;
   private String unitsProp_ = null;
   
   private String prevUnits_;
   
   private final PanmirrorImageChooser url_;
   private final NumericTextBox width_;
   private final NumericTextBox height_;
   private final FormLabel heightAuto_;
   private final ListBox units_;
   private final CheckBox lockRatio_;
   private final TextBox title_;
   private final TextBox alt_;
   private final PanmirrorEditAttrWidget editAttr_;
}