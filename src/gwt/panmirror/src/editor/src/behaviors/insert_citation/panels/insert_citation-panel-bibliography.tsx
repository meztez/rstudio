import React from "react";

import { Node as ProsemirrorNode } from 'prosemirror-model';

import { FixedSizeList, ListChildComponentProps } from "react-window";

import { BibliographySource, BibliographyManager, BibliographyContainer } from "../../../api/bibliography/bibliography";
import { CitationPanelProps, CitationPanel } from "../insert_citation-picker";
import { EditorUI } from "../../../api/ui";
import { SelectTreeNode } from "../select_tree";


export const kAllLocalType = 'All Local Sources';


export const CitationListPanel: React.FC<CitationPanelProps> = props => {

  const bibMgr = props.bibliographyManager;
  const [itemData, setItemData] = React.useState<BibliographySource[]>([]);

  React.useEffect(() => {
    async function loadData() {
      if (props.selectedNode) {
        const selectedNode = props.selectedNode;
        if (selectedNode.type === kAllLocalType) {
          setItemData(bibMgr.allSources());
        } else {
          const provider = bibMgr.localProviders().find(prov => prov.name === selectedNode.type);
          if (provider) {
            if (selectedNode.key === provider.key) {
              setItemData(provider.items());
            } else {
              setItemData(provider.itemsForCollection(selectedNode.key));
            }
          }
        }
      }
    }
    loadData();

    // load the right panel
  }, [props.selectedNode]);

  const filteredItemData = itemData.filter(data => !props.sourcesToAdd.map(source => source.id).includes(data.id));

  return (
    <FixedSizeList
      height={500}
      width='100%'
      itemCount={filteredItemData.length}
      itemSize={50}
      itemData={{ data: filteredItemData, addSource: props.addSource }}
    >
      {CitationListItem}
    </FixedSizeList>);
};


export function bibliographyPanel(doc: ProsemirrorNode, ui: EditorUI, bibliographyManager: BibliographyManager): CitationPanel {
  const providers = bibliographyManager.localProviders();
  const localProviderNodes = providers.map(provider => {
    const node: any = {};
    node.key = provider.key;
    node.name = provider.name;
    node.type = provider.name;
    node.children = toTree(provider.name, provider.containers(doc, ui));
    return node;
  });

  return {
    key: '17373086-77FE-410F-A319-33E314482125',
    panel: CitationListPanel,
    treeNode: {
      key: 'My Sources',
      name: 'My Sources',
      type: kAllLocalType,
      children: localProviderNodes,
      expanded: true
    }
  };
}

interface CitationListData {
  data: BibliographySource[];
  addSource: (source: BibliographySource) => void;
}

const CitationListItem = (props: ListChildComponentProps) => {

  const citationListData: CitationListData = props.data;
  const source = citationListData.data[props.index];

  const onClick = (event: React.MouseEvent) => {
    citationListData.addSource(source);
  };

  return (<div style={props.style} onClick={onClick}>
    {source.title}
  </div>);
};

// Takes a flat data structure of containers and turns it into a hierarchical
// tree structure for display as TreeNodes.
function toTree(type: string, containers: BibliographyContainer[]): SelectTreeNode[] {

  const treeMap: { [id: string]: SelectTreeNode } = {};
  const rootNodes: SelectTreeNode[] = [];

  containers.sort((a, b) => a.name.localeCompare(b.name)).forEach(container => {

    // First see if we have an existing node for this item
    // A node could already be there if we had to insert a 'placeholder' 
    // node to contain the node's children before we encountered the node.
    const currentNode = treeMap[container.key] || { key: container.key, name: container.name, children: [], type };

    // Always set its name to be sure we fill this in when we encounter it
    currentNode.name = container.name;

    if (container.parentKey) {
      let parentNode = treeMap[container.parentKey];
      if (!parentNode) {
        // This is a placeholder node - we haven't yet encountered this child's parent
        // so we insert this to hold the child. Once we encounter the true parent node, 
        // we will fix up the values in this placeholder node.
        parentNode = { key: container.parentKey, name: '', children: [], type };
        treeMap[container.parentKey] = parentNode;
      }
      parentNode.children?.push(currentNode);
    } else {
      rootNodes.push(currentNode);
    }
    treeMap[container.key] = currentNode;
  });
  return rootNodes;
}

