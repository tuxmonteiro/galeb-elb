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
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeRulesRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeRulesResult;
import com.amazonaws.services.elasticloadbalancingv2.model.Listener;
import com.amazonaws.services.elasticloadbalancingv2.model.Rule;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AlbService {

    @SuppressWarnings("unused")
    private enum FieldType {
        HOST_HEADER("host-header"),
        PATH_PATTERN("path-pattern");

        private final String fieldName;

        @Override
        public String toString() {
            return fieldName;
        }

        FieldType(String fieldName) {
            this.fieldName = fieldName;
        }
    }

    private final UpdateService updateService;
    private final DnsSyncService dnsSyncService;

    @Autowired
    public AlbService(UpdateService updateService, DnsSyncService dnsSyncService) {
        this.updateService = updateService;
        this.dnsSyncService = dnsSyncService;
    }

    public String sync() {
        try {
            Map<String, Object> config = updateService.getConfig();
            final AmazonElasticLoadBalancing loadBalancing = AmazonElasticLoadBalancingClient.builder().build();

            final DescribeLoadBalancersRequest describeLoadBalancersRequest = new DescribeLoadBalancersRequest();
            final DescribeListenersRequest describeListenersRequest = new DescribeListenersRequest();
            final DescribeRulesRequest describeRulesRequest = new DescribeRulesRequest();

            final DescribeListenersResult listenersResult = loadBalancing
                .describeListeners(describeListenersRequest);

            listenersResult.getListeners().stream().map(Listener::getListenerArn).map(listenersArn -> loadBalancing
                .describeRules(describeRulesRequest.withListenerArn(listenersArn)));


            final DescribeRulesResult rulesResult = loadBalancing.describeRules(describeRulesRequest);
            final List<Rule> rules = rulesResult.getRules();

            final String vhost= "";
            final boolean exist = rules.stream()
                .anyMatch(rule -> rule.getConditions().stream().anyMatch(ruleCondition -> ruleCondition
                    .getField().equals(FieldType.HOST_HEADER.toString()) && ruleCondition.getValues().contains
                    (vhost)));

            dnsSyncService.sync(config);

            return "{\"status\":\"OK\"}";
        } catch (Exception e) {
            return "{\"status\":\"FAIL\", \"statusDetails\":\"" + e.getMessage() + "\"}";
        }
    }
}
