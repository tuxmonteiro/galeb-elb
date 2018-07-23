/*
 * Copyright (c) 2017-2018 Globo.com
 * All rights reserved.
 *
 * This source is subject to the Apache License, Version 2.0.
 * Please see the LICENSE file for more information.
 *
 * Authors: See AUTHORS file
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.galeb.elb.services;

import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeListenersRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeListenersResult;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersResult;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeRulesRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeRulesResult;
import com.amazonaws.services.elasticloadbalancingv2.model.Listener;
import com.amazonaws.services.elasticloadbalancingv2.model.LoadBalancer;
import com.amazonaws.services.elasticloadbalancingv2.model.Rule;
import com.amazonaws.services.elasticloadbalancingv2.model.RuleCondition;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AlbService {

    private static final String HOST_HEADER = "host-header";
    private static final String PATH_PATTERN = "path-pattern";

    private static final Logger LOG = Logger.getLogger(AlbService.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final UpdateService updateService;
    private final DnsSyncService dnsSyncService;

    @Autowired
    public AlbService(UpdateService updateService, DnsSyncService dnsSyncService) {
        this.updateService = updateService;
        this.dnsSyncService = dnsSyncService;
    }

    public Map<String, Object> sync(){
        // call Galeb Manager
        final Map<String, Object> config = updateService.getConfig();

        // get AWS/ELB info
        final Map<String, Object> elbInfo = getAllGalebElb();

        // process diffs
        processDiffs(config, elbInfo);

        // return current elbInfo
        return getAllGalebElb();
    }

    private boolean processDiffs(Map<String, Object> config, Map<String, Object> elbInfo) {
        final Object virtualhostsObj = config.get("virtualhosts");
        if (virtualhostsObj instanceof List) {
            List virtualhosts = (List) virtualhostsObj;
            virtualhosts.forEach(virtualhostObj -> {
                if (virtualhostObj instanceof Map) {
                    Map virtualhost = (Map) virtualhostObj;
                    final String virtualhostName = String.valueOf(virtualhost.get("name"));
                    final Map loadBalancer = getAwsLoadBalancer(virtualhostName, elbInfo);
                    final Object rulesObj = virtualhost.get("rules");
                    if (rulesObj instanceof List) {
                        List rules = (List) rulesObj;
                        if (rules.size() == 1 && rules.get(0) instanceof Map) {
                            Map rule = (Map) rules.get(0);
                            final Object poolObj = rule.get("pool");
                            final Map pool;
                            if (poolObj instanceof Map && (pool = (Map) poolObj).get("targets") instanceof List &&
                                (!loadBalancer.isEmpty() || createVirtualhost(virtualhostName))) {

                                final Object targetsObj = pool.get("targets");
                                if (targetsObj instanceof List && targetsChanged((List) targetsObj, loadBalancer)) {
                                    syncTargets((List) targetsObj, loadBalancer);
                                }
                            }
                        }
                    }
                }
            });
        }
        return false;
    }


    private Map getLoadBalancerFromRouter53(String virtualhostName) {
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private Map getAwsLoadBalancer(String virtualhostName, Map<String, Object> elbInfo) {
        Object loadBalancersObj = elbInfo.get("loadBalancers");
        if (loadBalancersObj instanceof List) {
            final List loadBalancers = (List) loadBalancersObj;
            for (Object loadBalancerObj: loadBalancers) {
                if (loadBalancerObj instanceof Map) {
                    Map loadBalancer = (Map) loadBalancerObj;
                    final String loadBalancerType = String.valueOf(loadBalancer.get("loadBalancerType"));
                    if ("network".equals(loadBalancerType)) {
                        return getLoadBalancerFromRouter53(virtualhostName);
                    } else {
                        final Object listenersObj = loadBalancer.get("listeners");
                        if (listenersObj instanceof List) {
                            for (Object listenerObj : (List) listenersObj) {
                                if (listenerObj instanceof Map) {
                                    Map listener = (Map) listenerObj;
                                    final Object rulesObj = listener.get("rules");
                                    if (rulesObj instanceof List) {
                                        for (Object ruleObj : (List) rulesObj) {
                                            if (ruleObj instanceof Map) {
                                                Map rule = (Map) ruleObj;
                                                final Object conditionsObj = rule.get("conditions");
                                                if (conditionsObj instanceof List) {
                                                    List<RuleCondition> conditions = (List<RuleCondition>) conditionsObj;
                                                    if (conditions.stream()
                                                        .anyMatch(condition -> HOST_HEADER.equals(condition.getField()) && condition.getValues().contains(virtualhostName))) {

                                                        return loadBalancer;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return Collections.emptyMap();
    }

    private void syncTargets(List config, Map loadBalancer) {

    }

    private boolean targetsChanged(List config, Map loadBalancer) {
        return false;
    }

    private boolean createVirtualhost(String virtualhostName) {
        Map<String, Object> newLoadBalancer = new LinkedHashMap<>();
        dnsSyncService.sync(virtualhostName, newLoadBalancer);
        return false;
    }

    private boolean contains(String virtualhostName, Map<String, Object> elbInfo) {
        return false;
    }

    public Map<String, Object> getAllGalebElb() {
        try {
            final AmazonElasticLoadBalancing elbv2 = AmazonElasticLoadBalancingClient.builder().build();

            final DescribeLoadBalancersRequest describeLoadBalancersRequest = new DescribeLoadBalancersRequest();
            describeLoadBalancersRequest.putCustomQueryParameter("starts_with(LoadBalancerName,`galeb-`)", null);

            final DescribeLoadBalancersResult loadBalancersResult = elbv2.describeLoadBalancers(describeLoadBalancersRequest);
            final List<LoadBalancer> loadBalancers = loadBalancersResult.getLoadBalancers();

            final Map<String, Object> map = new LinkedHashMap<>();
            final ArrayList<Object> arrayOfLoadBalancers = new ArrayList<>();
            map.put("loadBalancers", arrayOfLoadBalancers);

            loadBalancers.forEach(loadBalancer -> {
                final Map<String, Object> loadBalancerNode = new LinkedHashMap<>();
                arrayOfLoadBalancers.add(loadBalancerNode);
                loadBalancerNode.put("loadBalancer", loadBalancer.getLoadBalancerName());
                loadBalancerNode.put("loadBalancerArn", loadBalancer.getLoadBalancerArn());
                loadBalancerNode.put("loadBalancerType", loadBalancer.getType());

                final DescribeListenersRequest describeListenersRequest = new DescribeListenersRequest()
                    .withLoadBalancerArn(loadBalancer.getLoadBalancerArn());
                final DescribeListenersResult listenersResult = elbv2.describeListeners(describeListenersRequest);
                final List<Listener> listeners = listenersResult.getListeners();

                final ArrayList<Object> arrayOfListeners = new ArrayList<>();
                loadBalancerNode.put("listeners", arrayOfListeners);

                listeners.forEach(listener -> {
                    Map<String, Object> listenerNode = new LinkedHashMap<>();
                    arrayOfListeners.add(listenerNode);
                    String listenerArn = listener.getListenerArn();
                    listenerNode.put("listenerPort", listener.getPort());
                    listenerNode.put("listenerArn", listenerArn);

                    final DescribeRulesRequest describeRulesRequest = new DescribeRulesRequest().withListenerArn(listenerArn);
                    final DescribeRulesResult rulesResult = elbv2.describeRules(describeRulesRequest);
                    final List<Rule> rules = rulesResult.getRules();

                    final ArrayList<Object> arrayOfRules = new ArrayList<>();
                    listenerNode.put("rules", arrayOfRules);

                    rules.forEach(rule -> {
                        Map<String, Object> ruleNode = new LinkedHashMap<>();
                        ruleNode.put("ruleArn", rule.getRuleArn());
                        ruleNode.put("actions", rule.getActions());
                        ruleNode.put("conditions", rule.getConditions());
                        ruleNode.put("priority", rule.getPriority());
                        ruleNode.put("default", rule.getIsDefault());
                        arrayOfRules.add(ruleNode);
                    });
                });
            });

            return map;
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            return Collections.singletonMap("error", e.getMessage());
        }
    }
}
